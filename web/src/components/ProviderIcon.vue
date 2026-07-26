<script setup lang="ts">
/**
 * Provider 图标：优先自定义图，否则内置品牌标；未知协议用几何回退（不用首字母）。
 */
import { computed } from 'vue'
import claudeSvg from '@/assets/providers/claude.svg'
import cursorSvg from '@/assets/providers/cursor.svg'
import openaiSvg from '@/assets/providers/openai.svg'
import opencodeSvg from '@/assets/providers/opencode.svg'
import geminiSvg from '@/assets/providers/gemini.svg'
import copilotSvg from '@/assets/providers/copilot.svg'
import aiderSvg from '@/assets/providers/aider.svg'
import gooseSvg from '@/assets/providers/goose.svg'
import codebuddySvg from '@/assets/providers/codebuddy.svg'
import qwenSvg from '@/assets/providers/qwen.svg'
import kimiSvg from '@/assets/providers/kimi.svg'
import qoderSvg from '@/assets/providers/qoder.svg'
import traecliSvg from '@/assets/providers/traecli.svg'
import kiroSvg from '@/assets/providers/kiro.svg'
import grokSvg from '@/assets/providers/grok.svg'
import hermesSvg from '@/assets/providers/hermes.svg'
import piSvg from '@/assets/providers/pi.svg'
import openclawSvg from '@/assets/providers/openclaw.svg'
import antigravitySvg from '@/assets/providers/antigravity.svg'
import devecoSvg from '@/assets/providers/deveco.svg'
import { baseProviderOf } from '@/lib/runtimes'

const BUILTIN: Record<string, string> = {
  claude: claudeSvg,
  claude_code: claudeSvg,
  cursor: cursorSvg,
  codex: openaiSvg,
  openai: openaiSvg,
  opencode: opencodeSvg,
  gemini: geminiSvg,
  copilot: copilotSvg,
  aider: aiderSvg,
  goose: gooseSvg,
  codebuddy: codebuddySvg,
  qwen: qwenSvg,
  kimi: kimiSvg,
  qoder: qoderSvg,
  traecli: traecliSvg,
  kiro: kiroSvg,
  grok: grokSvg,
  hermes: hermesSvg,
  pi: piSvg,
  openclaw: openclawSvg,
  antigravity: antigravitySvg,
  deveco: devecoSvg,
}

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
  if (BUILTIN[p]) return p
  return 'fallback'
})

const assetSrc = computed(() => {
  if (props.customSrc) return ''
  const p = base.value
  if (kind.value === 'claude') return claudeSvg
  if (kind.value === 'cursor') return cursorSvg
  if (kind.value === 'codex') return openaiSvg
  return BUILTIN[p] || ''
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

    <!-- 未知协议：几何块，避免首字母撞脸 -->
    <svg v-else viewBox="0 0 24 24" aria-hidden="true">
      <rect width="24" height="24" rx="6" fill="#e5e7eb" />
      <rect x="5" y="5" width="6" height="6" rx="1.5" fill="#9ca3af" />
      <rect x="13" y="5" width="6" height="6" rx="1.5" fill="#6b7280" />
      <rect x="5" y="13" width="6" height="6" rx="1.5" fill="#6b7280" />
      <rect x="13" y="13" width="6" height="6" rx="1.5" fill="#9ca3af" />
    </svg>
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
.provider-icon.gemini {
  background: #eff6ff;
  padding: 3px;
  box-sizing: border-box;
}
.provider-icon.copilot {
  background: #f6f8fa;
  padding: 2px;
  box-sizing: border-box;
}
.provider-icon.stub,
.provider-icon.custom,
.provider-icon.fallback {
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
</style>
