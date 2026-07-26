<script setup lang="ts">
/**
 * Provider 图标：优先自定义图，否则内置品牌标（Claude / Cursor / Codex 用官方 SVG）。
 */
import { computed } from 'vue'
import claudeSvg from '@/assets/providers/claude.svg'
import cursorSvg from '@/assets/providers/cursor.svg'
import openaiSvg from '@/assets/providers/openai.svg'

const props = withDefaults(
  defineProps<{
    provider: string
    size?: number
    title?: string
    /** 自定义图标 data URL / http(s) */
    customSrc?: string
  }>(),
  { size: 22, customSrc: '' },
)

const kind = computed(() => {
  if (props.customSrc) return 'custom'
  const p = (props.provider || '').toLowerCase()
  if (p === 'claude_code' || p === 'claude') return 'claude'
  if (p === 'codex' || p === 'openai') return 'codex'
  if (p === 'cursor') return 'cursor'
  if (p === 'stub') return 'stub'
  return 'fallback'
})

const letter = computed(() => (props.provider || '?').slice(0, 1).toUpperCase())

const assetSrc = computed(() => {
  if (kind.value === 'claude') return claudeSvg
  if (kind.value === 'cursor') return cursorSvg
  if (kind.value === 'codex') return openaiSvg
  return ''
})
</script>

<template>
  <span
    class="provider-icon"
    :class="kind"
    :style="{ width: `${size}px`, height: `${size}px` }"
    :title="title || provider"
    role="img"
    :aria-label="title || provider"
  >
    <img v-if="kind === 'custom'" class="asset-img" :src="customSrc" alt="" />
    <img v-else-if="assetSrc" class="asset-img" :src="assetSrc" alt="" />

    <!-- stub -->
    <svg v-else-if="kind === 'stub'" viewBox="0 0 24 24" aria-hidden="true">
      <rect width="24" height="24" rx="6" fill="#e5e7eb" />
      <path
        fill="#6b7280"
        d="M8 8h3.2v8H8V8zm4.8 0H16c1.66 0 2.8 1.05 2.8 2.55S17.66 13.1 16 13.1h-1.4V16h-1.8V8zm1.8 3.55H16c.55 0 1-.35 1-.9s-.45-.9-1-.9h-1.4v1.8z"
      />
    </svg>

    <!-- 未知：回退首字母 -->
    <span v-else class="fallback-letter">{{ letter }}</span>
  </span>
</template>

<style scoped>
.provider-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 6px;
  overflow: hidden;
  background: #f3f4f6;
  vertical-align: middle;
}
.provider-icon.claude {
  background: #fff7ed;
  padding: 2px;
  box-sizing: border-box;
}
.provider-icon.codex {
  background: #f9fafb;
  padding: 2px;
  box-sizing: border-box;
}
.provider-icon.cursor {
  background: #141414;
  padding: 3px;
  box-sizing: border-box;
}
.provider-icon.stub,
.provider-icon.custom {
  background: #f3f4f6;
}
.provider-icon svg {
  width: 100%;
  height: 100%;
  display: block;
}
.asset-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}
.fallback-letter {
  font-size: 11px;
  font-weight: 700;
  color: #374151;
  line-height: 1;
}
</style>
