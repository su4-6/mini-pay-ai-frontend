import { IS_MINIPAY_FOOD } from '@/config'
import { exchangeMiniPayHandoff } from '@/api/minipay-food'
import { userGetUserInfo } from '@/api/user'
import { useMainStore } from '@/store/store'

const pending = new Map()
let installed = false
let navigationInterceptorsInstalled = false
const FOOD_HOME_ROUTE = 'pages/index/index'
const FOOD_TAB_ROUTES = new Set([
  FOOD_HOME_ROUTE,
  'pages/menu/menu',
  'pages/order/order',
  'pages/mine/mine',
])

function bridgeObject() {
  return typeof window !== 'undefined' ? window.MiniPayFoodBridge : null
}

function requestId() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function post(type, payload = {}) {
  const bridge = bridgeObject()
  if (!bridge || typeof bridge.postMessage !== 'function') {
    return Promise.reject(new Error('MINIPAY_BRIDGE_REQUIRED'))
  }
  const id = requestId()
  bridge.postMessage(JSON.stringify({ version: 3, type, requestId: id, payload }))
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      pending.delete(id)
      reject(new Error('MINIPAY_BRIDGE_TIMEOUT'))
    }, 65000)
    pending.set(id, { resolve, reject, timeout })
  })
}

function emit(type, payload = {}) {
  const bridge = bridgeObject()
  if (!bridge || typeof bridge.postMessage !== 'function') return false
  bridge.postMessage(JSON.stringify({ version: 3, type, requestId: requestId(), payload }))
  return true
}

function currentRoute() {
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
  if (pages.length) return pages[pages.length - 1].route || ''
  if (typeof window === 'undefined') return ''
  return window.location.hash.replace(/^#\/?/, '').split('?')[0]
}

function navigationState() {
  const route = currentRoute()
  emit('NAVIGATION_STATE', { canGoBack: !!route && route !== FOOD_HOME_ROUTE, routeId: route })
}

function installNavigationInterceptors() {
  if (navigationInterceptorsInstalled || typeof uni === 'undefined' || !uni.addInterceptor) return
  navigationInterceptorsInstalled = true
  ;['navigateTo', 'redirectTo', 'switchTab', 'reLaunch', 'navigateBack'].forEach(method => {
    uni.addInterceptor(method, {
      complete: () => setTimeout(navigationState, 0),
    })
  })
}

function receive(event) {
  let envelope = event && event.data
  if (typeof envelope === 'string') {
    try { envelope = JSON.parse(envelope) } catch (_) { return }
  }
  if (!envelope || envelope.version !== 3 || !envelope.requestId) return
  if (envelope.type === 'NAVIGATE_BACK') {
    const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
    const route = currentRoute()
    if (pages.length > 1) {
      uni.navigateBack({ delta: 1 })
    } else if (route === FOOD_HOME_ROUTE || !route) {
      emit('CLOSE')
    } else if (FOOD_TAB_ROUTES.has(route)) {
      uni.switchTab({ url: `/${FOOD_HOME_ROUTE}` })
    } else {
      uni.navigateBack({
        delta: 1,
        fail: () => uni.switchTab({ url: `/${FOOD_HOME_ROUTE}` }),
      })
    }
    return
  }
  if (!['AUTHORIZATION_CODE', 'AUTHORIZATION_CHANGED', 'PAYMENT_RESULT', 'LOCATION_CONTEXT', 'WALLET_BALANCE'].includes(envelope.type)) return
  const waiter = pending.get(envelope.requestId)
  if (!waiter) {
    if (envelope.type === 'PAYMENT_RESULT') {
      const status = envelope.payload && envelope.payload.status
      uni.showToast({
        title: status === 'SUCCEEDED' ? '支付成功' : '支付状态已更新',
        icon: status === 'SUCCEEDED' ? 'success' : 'none',
      })
    }
    return
  }
  clearTimeout(waiter.timeout)
  pending.delete(envelope.requestId)
  waiter.resolve(envelope)
}

function install() {
  if (installed || typeof window === 'undefined') return
  installed = true
  window.addEventListener('message', receive)
  window.addEventListener('popstate', () => setTimeout(navigationState, 0))
  installNavigationInterceptors()
  setTimeout(navigationState, 0)
}

export async function initializeMiniPayFood() {
  if (!IS_MINIPAY_FOOD) return false
  install()
  if (!bridgeObject()) {
    uni.showModal({
      title: '请在 MiniPay 中打开',
      content: '该外卖版本只能在 MiniPay 中完成授权和付款。',
      showCancel: false,
    })
    return false
  }
  const envelope = await post('REQUEST_AUTHORIZATION')
  const payload = envelope.payload || {}
  const login = await exchangeMiniPayHandoff(payload.authorizationCode, payload.deviceProof)
  const main = useMainStore()
  main.SET_TOKEN(login.accessToken)
  const member = await userGetUserInfo({})
  if (member) main.SET_MEMBER(member)
  return true
}

export function miniPayAuthorizationErrorCode(error) {
  return error && (
    error.code ||
    (error.data && error.data.code) ||
    (error.response && error.response.data && error.response.data.code) ||
    (error.data && error.data.data && error.data.data.code)
  ) || 'MINIPAY_AUTHORIZATION_FAILED'
}

export function miniPayAuthorizationErrorMessage(error) {
  const code = miniPayAuthorizationErrorCode(error)
  if (code === 'NETWORK_UNAVAILABLE') return '无法连接意向点餐服务，请检查网络后重试'
  if (['MINIPAY_HANDOFF_INVALID', 'COMMERCE_HANDOFF_INVALID', 'COMMERCE_HANDOFF_REPLAYED'].includes(code)) {
    return '登录凭证已失效，请重新授权'
  }
  if (code === 'YSHOP_ACCOUNT_ALREADY_BOUND') {
    return '该手机号对应的意向点餐账号已绑定其他 MiniPay'
  }
  if (code === 'YSHOP_PHONE_ACCOUNT_CONFLICT') {
    return '该手机号对应多个意向点餐账号，请联系平台处理'
  }
  if (code === 'YSHOP_ACCOUNT_DISABLED') return '该意向点餐账号已停用，请联系平台处理'
  if (code === 'MINIPAY_IDENTITY_NOT_FOUND') return '账号绑定尚未完成，请重新授权'
  return '意向点餐登录失败，请稍后重试'
}

export async function requestNativeFoodPayment(externalOrderNo) {
  if (!IS_MINIPAY_FOOD) throw new Error('MINIPAY_BUILD_REQUIRED')
  return post('REQUEST_NATIVE_PAYMENT', { externalOrderNo })
}

export function closeMiniPayFood() {
  if (IS_MINIPAY_FOOD && emit('CLOSE')) return
  uni.switchTab({ url: `/${FOOD_HOME_ROUTE}` })
}

export async function requestMiniPayLocationContext() {
  if (!IS_MINIPAY_FOOD) throw new Error('MINIPAY_BUILD_REQUIRED')
  const envelope = await post('REQUEST_LOCATION_CONTEXT')
  return envelope.payload || { status: 'UNAVAILABLE' }
}

export async function requestMiniPayWalletBalance() {
  if (!IS_MINIPAY_FOOD) throw new Error('MINIPAY_BUILD_REQUIRED')
  const envelope = await post('REQUEST_WALLET_BALANCE')
  return envelope.payload || { status: 'UNAVAILABLE' }
}

export function notifyMiniPayNavigationState() {
  if (IS_MINIPAY_FOOD) navigationState()
}
