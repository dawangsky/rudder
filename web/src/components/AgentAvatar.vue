<script setup lang="ts">
/** 智能体头像展示：有图用图，否则回退 Provider 图标。 */
import ProviderIcon from '@/components/ProviderIcon.vue'

withDefaults(
  defineProps<{
    src?: string | null
    provider?: string
    size?: number
    rounded?: number | string
  }>(),
  { src: '', provider: '', size: 40, rounded: '28%' },
)
</script>

<template>
  <span
    class="agent-avatar"
    :style="{
      width: size + 'px',
      height: size + 'px',
      borderRadius: typeof rounded === 'number' ? rounded + 'px' : rounded,
    }"
  >
    <img v-if="src" :src="src" alt="" :width="size" :height="size" />
    <ProviderIcon v-else-if="provider" :provider="provider" :size="Math.round(size * 0.72)" />
    <span v-else class="fallback">✦</span>
  </span>
</template>

<style scoped>
.agent-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #f3f4f6;
  flex-shrink: 0;
}
.agent-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.fallback {
  font-size: 14px;
  color: #9ca3af;
}
</style>
