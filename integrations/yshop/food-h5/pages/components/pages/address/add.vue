<template>
	<uv-navbar
	  :fixed="false"
	  :title="title"
	  left-arrow
	  @leftClick="$onClickLeft"
	/>
	<view class="container address-form">
		<list-cell :hover="false">
			<view class="address-form__field">
				<view class="address-form__label">收货人</view>
				<input class="address-form__input" placeholder="请输入收货人" v-model="form.realName" placeholder-class="text-color-assist" />
			</view>
		</list-cell>
		<list-cell :hover="false">
			<view class="address-form__field">
				<view class="address-form__label">联系方式</view>
				<input class="address-form__input" placeholder="请输入收货人联系方式" v-model="form.phone" placeholder-class="text-color-assist" />
			</view>
		</list-cell>
		<list-cell :hover="false">
			<view class="address-form__field">
				<view class="address-form__label">收货地址</view>
				<view class="address-form__input" @click="chooseLocation">{{ form.address ? form.address : (IS_MINIPAY_FOOD ? '使用 MiniPay 当前位置' : '请选择收货地址') }}</view>
			</view>
		</list-cell>
		<list-cell :hover="false">
			<view class="address-form__field">
				<view class="address-form__label">详细地址</view>
				<input class="address-form__input" placeholder="请输入收货人详细地址" v-model="form.detail" placeholder-class="text-color-assist" />
			</view>
		</list-cell>
		<list-cell :hover="false">
			<view class="address-form__field">
				<view class="address-form__label">默认地址</view>
				<view class="address-form__radio-group">
					<view class="address-form__radio" :class="{ 'address-form__radio--checked': !form.isDefault }" @tap="form.isDefault = 0">否</view>
					<view class="address-form__radio" :class="{ 'address-form__radio--checked': form.isDefault }" @tap="form.isDefault = 1">是</view>
				</view>
			</view>
		</list-cell>
		<view class="address-form__actions">
			<button type="primary" size="default" class="rounded-pill address-form__btn" @tap="save">保存</button>
		</view>
	</view>
</template>

<script setup>
import {
  ref,
  computed
} from 'vue'
import { useMainStore } from '@/store/store'
import { storeToRefs } from 'pinia'
import { onLoad,onShow } from '@dcloudio/uni-app'
import { formatDateTime,prePage } from '@/utils/util'
import {
  getAddressAddAndEdit
} from '@/api/address'
import { IS_MINIPAY_FOOD } from '@/config'
import { requestMiniPayLocationContext } from '@/utils/minipay-food-bridge'
import { createMiniPayAddressLocationDraft, createMiniPayAddress } from '@/api/minipay-food'
const main = useMainStore()
const { isLogin,addresses } = storeToRefs(main)
const title = ref('编辑地址')
const locationDraft = ref(null)
const saving = ref(false)

const form = ref({
	realName: '',
	isDefault: 0,
	phone: '',
	address: '',
	detail: '',
	latitude: '',
	longitude: ''
})

onLoad((option) => {
	//为了方便演示，这里用本地缓存
	console.log('option:',option)
	if (option.id) {
		form.value = addresses.value.find(item => item.id == option.id)
	}
})

const save = async() => {
	if (saving.value) return
	if (IS_MINIPAY_FOOD && !form.value.id) {
		if (!locationDraft.value) {
			uni.showToast({ title: '请先获取收货位置', icon: 'none' })
			return
		}
		if (!form.value.realName || !/^1[3-9]\d{9}$/.test(form.value.phone) || !form.value.detail) {
			uni.showToast({ title: '请填写完整的联系人、手机号和门牌号', icon: 'none' })
			return
		}
		saving.value = true
		try {
			const idempotencyKey = `h5-address-${locationDraft.value.draftId}`
			const created = await createMiniPayAddress({
				draftId: locationDraft.value.draftId,
				recipient: form.value.realName,
				phone: form.value.phone,
				detail: form.value.detail,
				defaultAddress: form.value.isDefault === 1,
			}, idempotencyKey)
			main.SET_ADDRESS({
				id: created.addressId,
				realName: form.value.realName,
				phone: form.value.phone,
				address: created.displayAddress,
				detail: form.value.detail,
			})
			uni.showToast({ title: '地址已保存', icon: 'success' })
			setTimeout(() => uni.navigateBack(), 600)
		} catch (_) {
			uni.showToast({ title: '地址保存失败，请重新定位后重试', icon: 'none' })
		} finally {
			saving.value = false
		}
		return
	}
	let data = {}
	if (form.value.hasOwnProperty('id')) {
		data = await getAddressAddAndEdit(form.value);
	} else {
		data = await getAddressAddAndEdit(form.value);
	}
	if (data) {
		setTimeout(function(){
			uni.navigateBack()
		}, 2000);
	}
}

const chooseLocation = async() => {
	if (IS_MINIPAY_FOOD) {
		try {
			uni.showLoading({ title: '获取位置中' })
			const context = await requestMiniPayLocationContext()
			if (context.status !== 'READY' || !context.locationContextId) {
				uni.showToast({ title: '当前位置不可用，请检查 MiniPay 定位权限', icon: 'none' })
				return
			}
			main.SET_FOOD_LOCATION_CONTEXT(context)
			locationDraft.value = await createMiniPayAddressLocationDraft(context.locationContextId)
			form.value.address = locationDraft.value.displayAddress
		} catch (_) {
			uni.showToast({ title: '无法获取收货位置，请稍后重试', icon: 'none' })
		} finally {
			uni.hideLoading()
		}
		return
	}
	uni.chooseLocation({
		success: function (res) {
			form.value.address = res.address + ' ' + res.name;
			form.value.latitude = res.latitude;
			form.value.longitude = res.longitude;
		}
	});
	// let [error, res] = await uni.chooseLocation();
	// if (res) {
	// 	form.value.address = res.address + ' ' + res.name;
	// 	form.value.latitude = res.latitude;
	// 	form.value.longitude = res.longitude;
	// }
}

</script>

<style lang="scss" scoped>
$address-form-padding: $spacing-row-lg;
$address-form-label-width: 200rpx;
$address-form-radio-padding-y: 10rpx;
$address-form-radio-padding-x: $spacing-row-lg;
$address-form-radio-gap: 10rpx;
$address-form-radio-border-width: 2rpx;
$address-form-btn-height: 90rpx;
$address-form-btn-width: 85%;

.address-form {
	--address-form-label-width: #{$address-form-label-width};
	--address-form-btn-height: #{$address-form-btn-height};
	--address-form-btn-width: #{$address-form-btn-width};

	display: flex;
	flex-direction: column;
	width: 100%;
	height: 100%;
	padding: $address-form-padding;
	color: $text-color-base;

	&__field {
		display: flex;
		align-items: center;
		width: 100%;
	}

	&__label {
		flex-shrink: 0;
		width: var(--address-form-label-width);
		font-size: $font-size-lg;
		font-weight: 500;
	}

	&__input {
		flex: 1;
		display: flex;
		align-items: center;
	}

	&__radio-group {
		display: flex;
		gap: $address-form-radio-gap;
	}

	&__radio {
		padding: $address-form-radio-padding-y $address-form-radio-padding-x;
		border: $address-form-radio-border-width solid $text-color-assist;
		border-radius: $border-radius-base;
		color: $text-color-assist;
		font-size: $font-size-base;

		&--checked {
			border-color: $color-primary;
			background-color: $color-primary;
			color: $text-color-white;
		}
	}

	&__actions {
		flex: 1;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	&__btn {
		display: flex;
		align-items: center;
		justify-content: center;
		width: var(--address-form-btn-width);
		height: var(--address-form-btn-height);
		font-size: $font-size-base;
	}
}
</style>
