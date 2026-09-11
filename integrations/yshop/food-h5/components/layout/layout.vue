<template>
  <view class="layout">
    <slot />
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue';
const props = defineProps(['size'])
</script>

<style lang="less">
.layout {
  min-height: 100%;
}
</style>
