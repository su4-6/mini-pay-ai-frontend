<template>
	<uv-navbar
	  :fixed="false"
	  :title="title"
	  left-arrow
	  @leftClick="$onClickLeft"
	/>
	<view class="shop-page">
		<view class="shop-page__search">
			<uv-search v-model="keywork" @custom="search(keywork)" v-if="!IS_MINIPAY_FOOD"></uv-search>
			<view class="shop-page__message" v-if="message">{{ message }}</view>
			<uv-button v-if="IS_MINIPAY_FOOD && message" @click="getShop">重新查询</uv-button>
		</view>
		<view v-for="(item,index) in list" :key="index" class="shop-page__item">
			<uni-card @click="choice(item)" :border="item.id == store.id" :title="item.name" :thumbnail="item.image" :thumb-width="80" :sub-title="item.status_text">
				<view class="shop-page__body">
					<view class="shop-page__info">
						<view v-if="!item.directoryOnly">距离您 {{kmUnit(item.dis)}}</view>
						<view v-else>其他门店</view>
						<view v-if="IS_MINIPAY_FOOD && item.deliverable">支持配送，配送费：¥{{item.delivery_price}}</view>
						<view v-else-if="IS_MINIPAY_FOOD">当前地址不可配送</view>
						<view v-else-if="item.distance > 0">配送距离：{{item.distance + 'km '}} & 配送费：{{item.delivery_price}}</view>
						<view v-else>外卖不配送</view>
						<view>{{item.addressMap + ' ' + item.address}}</view>
						<view v-if="!IS_MINIPAY_FOOD">营业时间 {{formatDateTime(item.startTime,'hh:mm')}} - {{formatDateTime(item.endTime,'hh:mm')}}</view>
					</view>
					<view class="shop-page__actions" v-if="!IS_MINIPAY_FOOD">
						<uv-button @click="openLocation(item)">导航</uv-button>
						<uv-button @click="call(item.mobile)">致电</uv-button>
					</view>
				</view>
			</uni-card>
		</view>
	</view>
</template>

<script setup>
import {
  ref
} from 'vue'
import { useMainStore } from '@/store/store'
import { storeToRefs } from 'pinia'
import { onLoad,onShow ,onPullDownRefresh,onHide} from '@dcloudio/uni-app'
import { formatDateTime,kmUnit,prePage } from '@/utils/util'
import {
  shopNearby,
  menuGoods,
  getStoreList
} from '@/api/goods'
import {
  shopGetList
} from '@/api/market'
import { IS_MINIPAY_FOOD } from '@/config'
import { getMiniPayNearbyStores } from '@/api/minipay-food'
import { requestMiniPayLocationContext } from '@/utils/minipay-food-bridge'
import { locationContextIsFresh, mapMiniPayStore } from '@/utils/minipay-food-store'
const main = useMainStore()
const { store,location,orderType,address } = storeToRefs(main)
const title = ref('店铺')
const list = ref([])
const keywork = ref('')
const page = ref(1)
const pagesize = ref(10)
const message = ref('')
	
onLoad(() => {
	getShop();
})

const getShop = async(keywork = '') => {
	message.value = ''
	if (IS_MINIPAY_FOOD) {
		try {
			let source
			if (orderType.value === 'takeout' && address.value && address.value.id) {
				source = { addressId: address.value.id }
			} else {
				let context = main.foodLocationContext
				if (!locationContextIsFresh(context)) {
					context = await requestMiniPayLocationContext()
					if (context.status !== 'READY' || !context.locationContextId) {
						message.value = '当前位置不可用，请返回后重试或选择收货地址'
						list.value = []
						return
					}
					main.SET_FOOD_LOCATION_CONTEXT(context)
				}
				source = { locationContextId: context.locationContextId }
			}
			const stores = await getMiniPayNearbyStores(
				source,
				orderType.value === 'takein' ? 'PICKUP' : 'TAKEOUT'
			)
			const nearby = (stores || []).map(mapMiniPayStore)
			// 安全位置接口只返回当前服务半径内的门店；门店选择页还需要展示
			// 其他可选/暂不可配送门店，避免用户误以为系统只有一家门店。
			let allStores = []
			try {
				const legacyStores = await getStoreList({ lat: 0, lng: 0, kw: '', shop_id: 0 })
				allStores = (legacyStores || []).map(item => ({
					...item,
					directoryOnly: true,
					status_text: Number(item.status) === 1 ? '营业中' : '休息中',
					dis: Number(item.dis || 0),
					min_price: Number(item.min_price ?? item.minPrice ?? 0),
					delivery_price: Number(item.delivery_price ?? item.deliveryPrice ?? 0),
					deliverable: false,
				}))
			} catch (_) {
				// 完整门店目录不可用时，附近门店仍可正常选择。
			}
			const nearbyIds = new Set(nearby.map(item => String(item.id)))
			list.value = nearby.concat(allStores.filter(item => !nearbyIds.has(String(item.id))))
			main.SET_FOOD_STORE_CANDIDATES(stores || [])
			if (!list.value.length) message.value = '附近暂无符合条件的门店'
		} catch (_) {
			list.value = []
			message.value = '门店加载失败，请稍后重试'
		}
		return
	}
	let data = await shopGetList({
		lat: location.value.latitude ? location.value.latitude : 0,
		lng: location.value.longitude ? location.value.longitude : 0,
		kw: keywork,
		shop_id: 0
	});
	if (data) {
		//console.log(data);
		if (page.value == 1) {
			list.value = data;
		} else {
			for(let i in data) {
				list.value.push(data[i]);
			}
		}
	}
}
//打开定位
const openLocation = (shop) => {
	//console.log(shop);
	uni.openLocation({
		latitude: parseFloat(shop.lat),
		longitude: parseFloat(shop.lng),
		name:shop.name,
		address: shop.addressMap + shop.address,
		fail: (res) => {
			console.log(res);
		}
	})
}
// 打电话
const call = (mobile) => {
	uni.makePhoneCall({
		phoneNumber:mobile
	})
}
// 搜索按钮
const search = (keywork) => {
	page.value = 1;
	getShop(keywork);
}
// 选中店铺
const choice = (shop) => {
	if (Number(shop.status) !== 1) {
		uni.showToast({ title: '该门店当前休息中', icon: 'none' })
		return
	}
	if (IS_MINIPAY_FOOD && orderType.value === 'takeout' && !shop.deliverable) {
		uni.showToast({ title: '该门店暂不能配送到当前地址', icon: 'none' })
		return
	}
	if (store.value.id && store.value.id !== shop.id) {
		main.REMOVE_CART()
		uni.removeStorageSync('cart')
	}
	main.SET_STORE(shop);
	uni.$emit('refreshMenu')
	uni.switchTab({ 
		url:'/pages/menu/menu',
		success(res) {
		},
		fail(res) {
			console.log(res);
		}
	});
}

	
</script>

<style lang="scss">
$shop-page-search-margin: $spacing-row-lg;
$shop-page-info-padding-left: 6rpx;
$shop-page-actions-width: 20%;
$shop-page-actions-gap: $spacing-col-sm;

.shop-page {
	--shop-page-search-margin: #{$shop-page-search-margin};
	--shop-page-info-padding-left: #{$shop-page-info-padding-left};
	--shop-page-actions-width: #{$shop-page-actions-width};
	--shop-page-actions-gap: #{$shop-page-actions-gap};

	&__search {
		margin: var(--shop-page-search-margin);
	}

	&__body {
		display: flex;
		align-items: flex-start;
	}

	&__info {
		flex: 1;
		min-width: 0;
		padding-left: var(--shop-page-info-padding-left);
	}

	&__actions {
		flex: 0 0 var(--shop-page-actions-width);
		display: flex;
		flex-direction: column;
		gap: var(--shop-page-actions-gap);
	}
}
</style>
