<script setup lang="ts">
/**
 * Provider 图标：优先自定义图，否则内置品牌标；其余用着色首字母。
 */
import { computed } from 'vue'
import claudeSvg from '@/assets/providers/claude.svg'
import cursorSvg from '@/assets/providers/cursor.svg'
import openaiSvg from '@/assets/providers/openai.svg'
import { baseProviderOf } from '@/lib/runtimes'

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

const base = computed(() => baseProviderOf(props.provider || '').toLowerCase())

const kind = computed(() => {
  if (props.customSrc) return 'custom'
  const p = base.value
  if (p === 'claude_code' || p === 'claude') return 'claude'
  if (p === 'codex' || p === 'openai') return 'codex'
  if (p === 'cursor') return 'cursor'
  if (p === 'stub') return 'stub'
  return p || 'fallback'
})

const letter = computed(() => {
  const p = base.value || '?'
  if (p === 'claude_code') return 'C'
  if (p === 'opencode') return 'O'
  if (p === 'codebuddy') return 'B'
  if (p === 'traecli') return 'T'
  return p.slice(0, 1).toUpperCase()
})

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

    <svg v-else-if="kind === 'stub'" viewBox="0 0 24 24" aria-hidden="true">
      <rect width="24" height="24" rx="6" fill="#e5e7eb" />
      <path
        fill="#6b7280"
        d="M8 8h3.2v8H8V8zm4.8 0H16c1.66 0 2.8 1.05 2.8 2.55S17.66 13.1 16 13.1h-1.4V16h-1.8V8zm1.8 3.55H16c.55 0 1-.35 1-.9s-.45-.9-1-.9h-1.4v1.8z"
      />
    </svg>

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
.provider-icon.opencode { background: #ecfdf5; color: #047857; }
.provider-icon.gemini { background: #eff6ff; color: #1d4ed8; }
.provider-icon.copilot { background: #f5f3ff; color: #6d28d9; }
.provider-icon.aider { background: #fef3c7; color: #b45309; }
.provider-icon.goose { background: #fdf2f8; color: #be185d; }
.provider-icon.codebuddy { background: #fff1f2; color: #e11d48; }
.provider-icon.qwen { background: #fff7ed; color: #c2410c; }
.provider-icon.kimi { background: #f0f9ff; color: #0369a1; }
.provider-icon.qoder { background: #fdf4ff; color: #a21caf; }
.provider-icon.traecli { background: #ecfeff; color: #0e7490; }
.provider-icon.kiro { background: #fefce8; color: #a16207; }
.provider-icon.grok { background: #f4f4f5; color: #18181b; }
.provider-icon.hermes { background: #fef2f2; color: #b91c1c; }
.provider-icon.pi { background: #eef2ff; color: #4338ca; }
.provider-icon.openclaw { background: #f7fee7; color: #4d7c0f; }
.provider-icon.antigravity { background: #faf5ff; color: #7e22ce; }
.provider-icon.deveco { background: #ecfdf5; color: #0f766e; }
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
  color: inherit;
  line-height: 1;
}
</style>
