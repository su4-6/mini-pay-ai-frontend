import stringify from '@/utils/querystring'

import router from './router'
import cookie from './cookie'

export const handleLoginFailure = () => {
  // 登录页位于 subPackages（root: pages/components），H5 编译后路由为完整物理路径，
  // 必须带 root 前缀，否则 redirectTo 匹配失败导致页面停留空白
  uni.redirectTo({
    url: '/pages/components/pages/login/login',
  })
}

export function parseUrl(location) {
  if (typeof location === 'string') return location
  const { url, query } = location

  const queryStr = stringify(query)

  if (!queryStr) {
    return url
  }

  return `${url}?${queryStr}`
}

const toAuth = () => {
  uni.showToast({
    title: '暂未开放',
    icon: 'none',
    duration: 2000,
  })
}

export default {
  install: (app, options) => {
    // 在这里编写插件代码
    // 注入一个全局可用的 $translate() 方法
    app.config.globalProperties.$yrouter = router
    app.config.globalProperties.$cookie = cookie
    app.config.globalProperties.$toAuth = toAuth
    app.config.globalProperties.$onClickLeft = () => {
      router.back()
	  //uni.navigateBack()
	  //const mypage = getCurrentPages()
	  //console.log('mypage:',mypage)
    }

    // #ifdef H5
    app.config.globalProperties.$platform = 'h5'
    // #endif

    // #ifdef APP-PLUS
    // app端
    app.config.globalProperties.$platform = 'app'
    // #endif

    // #ifdef MP-WEIXIN
    app.config.globalProperties.$platform = 'routine'
    // #endif
  },
}
