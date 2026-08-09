<script setup lang="ts">
/** 根组件：路由出口 + Desktop 关闭确认弹窗。 */
import ClosePromptDialog from '@/components/ClosePromptDialog.vue'
</script>

<template>
  <router-view />
  <ClosePromptDialog />
</template>
