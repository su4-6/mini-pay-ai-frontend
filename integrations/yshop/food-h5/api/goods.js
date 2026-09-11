import api from './api'

/**
 * 获得banner列表
 */
export function shopNearby(data) {
  return api.get('/store/nearby', data, { login: false })
}
/**
 * 门店列表（不依赖定位精度，用于 MiniPay 门店 fallback）
 */
export function getStoreList(data) {
  return api.get('/store/list', data, { login: false })
}
/**
 * 获取首页信息
 */
export function menuGoods(data) {
  return api.get('/product/products', data, { login: false })
}

