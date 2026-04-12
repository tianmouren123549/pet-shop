/**
 * 一级主粮：狗粮=1、猫粮=2（与库表约定一致）
 */
const MAIN_FOOD_ROOT_IDS = new Set([1, 2])

export function groupRootCategories(allCategories) {
  const roots = (Array.isArray(allCategories) ? allCategories : []).filter(
    (c) => Number(c.parentId) === 0
  )
  const main = roots
    .filter((c) => MAIN_FOOD_ROOT_IDS.has(Number(c.categoryId)))
    .sort((a, b) => Number(a.categoryId) - Number(b.categoryId))
  const supplies = roots
    .filter((c) => !MAIN_FOOD_ROOT_IDS.has(Number(c.categoryId)))
    .sort((a, b) => Number(a.categoryId) - Number(b.categoryId))
  return { main, supplies }
}

/**
 * 首页/列表导航：除犬猫主粮（挂在一级 1、2 下）以外的**子类目**按钮，如零食、医疗保健、玩具等。
 * 不再展示「宠物用品」等笼统一级用品名，避免只出现一个聚合标签。
 */
export function getSupplyLeafNavCategories(allCategories) {
  const list = Array.isArray(allCategories) ? allCategories : []
  return list
    .filter((c) => {
      const pid = Number(c.parentId)
      if (pid === 0) return false
      if (MAIN_FOOD_ROOT_IDS.has(pid)) return false
      return true
    })
    .sort((a, b) => Number(a.categoryId) - Number(b.categoryId))
}

/**
 * 首页/列表：主粮一级（狗粮、猫粮）与用品子类同一序列顺排，无「主粮」分组行。
 */
export function getFlatCategoryNavCategories(allCategories) {
  const list = Array.isArray(allCategories) ? allCategories : []
  const main = list
    .filter(
      (c) =>
        Number(c.parentId) === 0 &&
        MAIN_FOOD_ROOT_IDS.has(Number(c.categoryId))
    )
    .sort((a, b) => Number(a.categoryId) - Number(b.categoryId))
  return [...main, ...getSupplyLeafNavCategories(list)]
}

/**
 * 商家/管理端下拉：按一级类目分组，组内为「整类」+ 子类目
 */
export function categorySelectOptgroups(allCategories) {
  const list = Array.isArray(allCategories) ? [...allCategories] : []
  const roots = list
    .filter((c) => Number(c.parentId) === 0)
    .sort((a, b) => Number(a.categoryId) - Number(b.categoryId))
  return roots.map((root) => {
    const rid = Number(root.categoryId)
    const children = list
      .filter((c) => Number(c.parentId) === rid)
      .sort((a, b) => Number(a.categoryId) - Number(b.categoryId))
    const options = []
    if (children.length > 0) {
      options.push({ categoryId: rid, label: `${root.name}（整类）` })
      for (const ch of children) {
        options.push({ categoryId: ch.categoryId, label: ch.name })
      }
    } else {
      options.push({ categoryId: rid, label: root.name })
    }
    return { groupLabel: root.name, options }
  })
}

export function firstSelectableCategoryId(optgroups) {
  const g = optgroups[0]
  if (!g?.options?.length) return null
  return g.options[0].categoryId
}
