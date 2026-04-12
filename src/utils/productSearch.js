/**
 * 犬/猫主粮相关词：与类目「进口/国产/冻干」及生命周期子类互通
 */
const DOG_FOOD_TERMS = [
  '狗粮',
  '成犬粮',
  '幼犬粮',
  '犬粮',
  '全犬粮',
  '大型犬粮',
  '小型犬粮',
  '进口狗粮',
  '国产狗粮',
  '冻干狗粮'
]

const CAT_FOOD_TERMS = [
  '猫粮',
  '幼猫粮',
  '成猫粮',
  '进口猫粮',
  '国产猫粮',
  '冻干猫粮',
  '全猫粮'
]

/**
 * 商品名称/ID/分类名/品牌等字段是否包含关键词（不区分大小写）
 */
export function productMatchesKeyword(p, keywordRaw) {
  const kw = String(keywordRaw || '').trim().toLowerCase()
  if (!kw) return true
  const parts = [p?.title, p?.productId, p?.categoryName, p?.brandName]
  const lowered = parts.map((x) => String(x ?? '').toLowerCase())
  if (lowered.some((s) => s.includes(kw))) return true

  const blob = lowered.join('\n')

  const queryTouchesDogFood = DOG_FOOD_TERMS.some((t) => {
    const tl = t.toLowerCase()
    return kw === tl || kw.includes(tl) || tl.includes(kw)
  })
  if (queryTouchesDogFood) {
    return DOG_FOOD_TERMS.some((t) => blob.includes(t.toLowerCase()))
  }

  const queryTouchesCatFood = CAT_FOOD_TERMS.some((t) => {
    const tl = t.toLowerCase()
    return kw === tl || kw.includes(tl) || tl.includes(kw)
  })
  if (queryTouchesCatFood) {
    return CAT_FOOD_TERMS.some((t) => blob.includes(t.toLowerCase()))
  }
  return false
}
