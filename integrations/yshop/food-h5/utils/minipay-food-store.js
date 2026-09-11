export function normalizeResourceUrl(value) {
  return typeof value === 'string' ? value.trim() : value
}

export function mapMiniPayStore(source) {
  return {
    ...source,
    id: source.shopId,
    image: normalizeResourceUrl(source.image),
    status: source.open ? 1 : 0,
    status_text: source.open ? '营业中' : '休息中',
    dis: Number(source.distanceMeters || 0),
    minPrice: Number(source.minimumOrderCent || 0) / 100,
    min_price: Number(source.minimumOrderCent || 0) / 100,
    deliveryPrice: Number(source.deliveryFeeCent || 0) / 100,
    delivery_price: Number(source.deliveryFeeCent || 0) / 100,
    deliverable: source.deliverable === true,
    notice: source.open ? '' : '门店当前不在营业时间',
    addressMap: '',
  }
}

export function normalizeMenuResources(categories) {
  if (!Array.isArray(categories)) return []
  return categories.map(category => ({
    ...category,
    icon: normalizeResourceUrl(category.icon || category.picUrl),
    picUrl: normalizeResourceUrl(category.picUrl),
    goodsList: Array.isArray(category.goodsList)
      ? category.goodsList.map(product => ({
          ...product,
          image: normalizeResourceUrl(product.image),
          sliderImage: normalizeResourceUrl(product.sliderImage),
        }))
      : [],
  }))
}

export function locationContextIsFresh(context, now = Date.now()) {
  if (!context || !context.locationContextId || !context.expiresAt) return false
  const expiresAt = Date.parse(context.expiresAt)
  return Number.isFinite(expiresAt) && expiresAt > now + 5000
}
