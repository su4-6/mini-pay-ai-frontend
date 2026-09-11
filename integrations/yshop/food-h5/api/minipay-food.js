import api from './api'

export function exchangeMiniPayHandoff(code, deviceProof) {
  return api.post('/minipay/auth/handoff', { code, deviceProof }, { login: false, sensitive: true })
}

export function createMiniPayFoodQuote(data) {
  return api.post('/minipay/food/checkout-quotes', data, { login: true })
}

export function getMiniPayNearbyStores(source, fulfillmentType = 'TAKEOUT') {
  const request = { fulfillmentType }
  if (source && source.locationContextId) request.locationContextId = source.locationContextId
  if (source && source.addressId) request.addressId = source.addressId
  return api.post('/minipay/food/stores/nearby', request, { login: true, sensitive: true })
}

export function createMiniPayAddressLocationDraft(locationContextId) {
  return api.post('/minipay/food/address-location-drafts', { locationContextId }, {
    login: true,
    sensitive: true,
  })
}

export function createMiniPayAddress(data, idempotencyKey) {
  return api.post('/minipay/food/addresses', data, {
    login: true,
    sensitive: true,
    headers: { 'Idempotency-Key': idempotencyKey },
  })
}

export function createMiniPayFoodOrder(data, idempotencyKey) {
  return api.post('/minipay/food/orders', data, {
    login: true,
    headers: { 'Idempotency-Key': idempotencyKey },
  })
}
