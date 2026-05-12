/**
 * 与后端 CategoryService.resolveCategoryIdsForProductFilter / mock findCategoryChildrenIds 一致：
 * 选子类目仅自身；选一级类目包含根 ID 及 path 以 {@code id/} 开头的子类目。
 */
export function resolveCategoryIdsForFilter(categoryId, allCategories) {
  const list = Array.isArray(allCategories) ? allCategories : []
  const hit = list.find((c) => Number(c.categoryId) === Number(categoryId))
  if (!hit) return new Set([Number(categoryId)])
  const normalizedName = String(hit.name || '').replace(/\s+/g, '').toLowerCase()

  const isDogFood = normalizedName.includes('狗粮') || normalizedName.includes('犬粮')
  const isCatFood = normalizedName.includes('猫粮')

  // “猫粮/狗粮”入口：合并同类目，覆盖不同来源的重复子类（进口/国产/成幼等）。
  if (isDogFood || isCatFood) {
    const ids = new Set()
    for (const c of list) {
      const n = String(c.name || '').replace(/\s+/g, '').toLowerCase()
      if (isDogFood && (n.includes('狗粮') || n.includes('犬粮'))) ids.add(Number(c.categoryId))
      if (isCatFood && n.includes('猫粮')) ids.add(Number(c.categoryId))
    }
    // 兜底保留当前分类 ID，避免误判为空。
    ids.add(Number(hit.categoryId))
    return ids
  }

  if (Number(hit.parentId) !== 0) return new Set([Number(hit.categoryId)])
  const prefix = `${hit.categoryId}/`
  const ids = new Set([Number(hit.categoryId)])
  for (const c of list) {
    const p = c.path
    if (p != null && String(p).startsWith(prefix)) {
      ids.add(Number(c.categoryId))
    }
  }
  return ids
}

export function productMatchesCategorySelection(product, selectedCategoryId, allCategories) {
  if (selectedCategoryId == null || selectedCategoryId === '') return true
  const ids = resolveCategoryIdsForFilter(selectedCategoryId, allCategories)
  return ids.has(Number(product?.categoryId))
}

/**
 * 展示为「父分类 · 子分类」，便于理解成犬粮/幼犬粮归属于狗粮
 */
export function formatCategoryWithParent(product, allCategories) {
  const list = Array.isArray(allCategories) ? allCategories : []
  const cid = Number(product?.categoryId)
  if (!cid) return String(product?.categoryName || '').trim() || '—'
  const c = list.find((x) => Number(x.categoryId) === cid)
  if (!c || c.parentId == null || Number(c.parentId) === 0) {
    return String(product?.categoryName || c?.name || '').trim() || '—'
  }
  const parent = list.find((x) => Number(x.categoryId) === Number(c.parentId))
  const childName = String(c.name || product?.categoryName || '').trim()
  if (!parent) return childName || '—'
  return `${parent.name} · ${childName}`
}
