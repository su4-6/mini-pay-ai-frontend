import { defineStore } from 'pinia'

import cookie from '@/utils/cookie'
import { navigateTo } from '@/utils/router'
import { normalizeResourceUrl } from '@/utils/minipay-food-store'

const normalizeMember = member => member && typeof member === 'object'
	? { ...member, avatar: normalizeResourceUrl(member.avatar) }
	: member

const memberId = member => member?.id == null ? null : String(member.id)
const CART_OWNER_KEY = 'cartOwnerMemberId'

export const useMainStore = defineStore('main', {
  state: () => ({
	store: {},
	cart: [],
	orderType: 'takein',
	address: {},
	addresses: {},
	member: {

	},
	openid:"",
	token:"",
	lang: 'zh-cn',
	cookieKey:'YSESSID=yshop-e4dk4o2utr3c0n95tp42p745ai',
	// 默认地为你为北京地址
	location: {},
	foodLocationContext: {},
	foodStoreCandidates: [],
	mycoupon: {}
  }),
  getters: {
	  
    isLogin(state) {//是否登录
      return Object.keys(state.member).length > 0
	  //return cookie.get('accessToken') ? true : false
    }
	//isLogin: state => Object.keys(state.member).length > 0	//是否登录
  },
  actions: {
	DEL_COUPON() {
	    	this.mycoupon = {}
	},
	SET_COUPON(coupon) {
	  	this.mycoupon = coupon
	},
	SET_ORDER_TYPE(type) {
	  	this.orderType = type
	},
	SET_MEMBER(member) {
		const normalizedMember = normalizeMember(member)
		const previousMemberId = memberId(this.member) || memberId(cookie.get('userinfo'))
		const nextMemberId = memberId(normalizedMember)
		const cartOwnerId = uni.getStorageSync(CART_OWNER_KEY)
		if (!nextMemberId ||
			(previousMemberId && previousMemberId !== nextMemberId) ||
			(cartOwnerId && String(cartOwnerId) !== nextMemberId)) {
			this.CLEAR_CART()
		}
		this.member = normalizedMember
		cookie.set('userinfo', normalizedMember)
	},
	SET_ADDRESS(address) {
		this.address = address
	},
	SET_ADDRESSES(addresses) {
		this.addresses = addresses
	},
	SET_STORE(store) {
		this.store = store
	},
	SET_CART(cart) {
		this.cart = Array.isArray(cart) ? cart : []
		if (this.cart.length === 0) {
			uni.removeStorageSync('cart')
			uni.removeStorageSync(CART_OWNER_KEY)
			return
		}
		uni.setStorageSync('cart', JSON.parse(JSON.stringify(this.cart)))
		const ownerId = memberId(this.member)
		if (ownerId) uni.setStorageSync(CART_OWNER_KEY, ownerId)
	},
	CLEAR_CART() {
		this.cart = []
		uni.removeStorageSync('cart')
		uni.removeStorageSync(CART_OWNER_KEY)
	},
	REMOVE_CART() {
		this.CLEAR_CART()
	},
	setCookie(state, provider) {
		state.cookie = provider;
		uni.setStorage({
			key: 'cookieKey',
			data: provider
		});
	},
	SET_LOCATION(location) {
		this.location = location;
	},
	SET_FOOD_LOCATION_CONTEXT(context) {
		this.foodLocationContext = context || {};
	},
	SET_FOOD_STORE_CANDIDATES(stores) {
		this.foodStoreCandidates = Array.isArray(stores) ? stores : [];
	},
	SET_OPENID(openid) {
		this.openid = openid;
	},
	SET_TOKEN(token) {
		this.token = token;
		cookie.set('accessToken', token)
	},
	  
    setAccessToken(user) {
      cookie.set('accessToken', user)
      // return getUserInfo()
    },
    setSelectAddress(id) {
      console.log('--> % setSelectAddress % id:\n', id)
      this.selectAddress = this.address.filter(item => item.id == id)[0]
    },
    init() {
      let accessToken = cookie.get('accessToken')
      if (accessToken) {
        // 恢复上次缓存的用户信息，避免页面闪现游客态
        let userinfo = cookie.get('userinfo')
        if (userinfo && Object.keys(userinfo).length > 0) {
		  this.member = normalizeMember(userinfo)
        }
      }
      return null
    },
    logout() {
      this.member = {}
      this.CLEAR_CART()
      this.token = ''
      cookie.remove('accessToken')
      cookie.remove('userinfo')
      navigateTo('/pages/login/login')
    },
  },
})
