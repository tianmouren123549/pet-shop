/**
 * 与后端 CategoryService.resolveCategoryIdsForProductFilter / mock findCategoryChildrenIds 一致：
 * 选子类目仅自身；选一级类目包含根 ID 及 path 以 {@code id/} 开头的子类目。
 */
export function resolveCategoryIdsForFilter(categoryId, allCategories) {
  const list = Array.isArray(allCategories) ? allCategories : []
  const hit = list.find((c) => Number(c.categoryId) === Number(categoryId))
  if (!hit) return new Set([Number(categoryId)])
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
