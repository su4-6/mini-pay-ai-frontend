<template>
  <view class="agreement-page">
    <text class="title">MiniPay 与意向点餐账号绑定协议</text>
    <text class="updated">更新日期：2026年8月9日</text>
    <text class="section">一、绑定内容</text>
    <text class="paragraph">在你主动确认并完成短信验证后，MiniPay 会将昵称、头像、经验证的绑定手机号以及当前位置授权状态提供给意向点餐，用于创建或识别你的意向点餐账号。</text>
    <text class="section">二、手机号与账号</text>
    <text class="paragraph">手机号仅用于识别、登录意向点餐以及配送和订单通知，不会替换你的 MiniPay 登录手机号。已有意向点餐账号将直接绑定；没有账号时将自动创建。</text>
    <text class="section">三、绑定限制</text>
    <text class="paragraph">一个意向点餐账号同一时间只能绑定一个 MiniPay 账号。账号停用、手机号对应多个账号或已被其他 MiniPay 绑定时，本次绑定不会完成。</text>
    <text class="section">四、解除绑定</text>
    <text class="paragraph">你可以在 MiniPay 的应用授权管理中解除绑定。解除后登录会话将失效，但意向点餐账号、地址和历史订单会按服务规则继续保留。</text>
    <text class="section">五、安全说明</text>
    <text class="paragraph">授权手机号采用加密方式保存，验证码只用于本次验证且不会写入业务日志。每次进入意向点餐使用短时、单次登录凭证。</text>
  </view>
</template>

<style scoped>
.agreement-page { min-height: 100vh; box-sizing: border-box; padding: 40rpx 36rpx 80rpx; background: #fff; color: #202124; }
.title, .updated, .section, .paragraph { display: block; }
.title { font-size: 38rpx; line-height: 1.4; font-weight: 700; }
.updated { margin: 16rpx 0 36rpx; color: #8a8f98; font-size: 24rpx; }
.section { margin-top: 28rpx; font-size: 30rpx; font-weight: 600; }
.paragraph { margin-top: 12rpx; color: #5b616b; font-size: 28rpx; line-height: 1.75; }
</style>
