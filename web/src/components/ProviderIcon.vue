<script setup lang="ts">
/**
 * Provider 图标：优先自定义图，否则内置官方品牌标。
 */
import { computed } from 'vue'

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
    <img v-if="kind === 'custom'" class="custom-img" :src="customSrc" alt="" />

    <!-- Claude / Anthropic 星芒标 -->
    <svg v-else-if="kind === 'claude'" viewBox="0 0 24 24" aria-hidden="true">
      <path
        fill="#D97757"
        d="M12 2.2c.45 0 .84.27 1.02.68l2.12 4.9 5.3.46c.95.08 1.34 1.25.62 1.87l-4.02 3.48 1.22 5.18c.22.93-.78 1.66-1.6 1.16L12 17.3l-4.66 2.63c-.82.46-1.82-.23-1.6-1.16l1.22-5.18-4.02-3.48c-.72-.62-.33-1.79.62-1.87l5.3-.46 2.12-4.9c.18-.41.57-.68 1.02-.68z"
      />
    </svg>

    <!-- OpenAI / Codex 花结标 -->
    <svg v-else-if="kind === 'codex'" viewBox="0 0 24 24" aria-hidden="true">
      <path
        fill="#0D0D0D"
        d="M22.2819 9.8211a5.9847 5.9847 0 0 0-.5157-4.9108 6.0462 6.0462 0 0 0-6.5098-2.9A6.0651 6.0651 0 0 0 4.9807 4.1818a5.9847 5.9847 0 0 0-3.9977 2.9 6.0462 6.0462 0 0 0 .7427 7.0966 5.98 5.98 0 0 0 .511 4.9107 6.051 6.051 0 0 0 6.5146 2.9001A5.9847 5.9847 0 0 0 13.2599 24a6.0557 6.0557 0 0 0 5.7718-4.2058 5.9894 5.9894 0 0 0 3.9977-2.9001 6.0557 6.0557 0 0 0-.7475-7.0729zm-9.022 12.6081a4.4755 4.4755 0 0 1-2.8764-1.0408l.1419-.0804 4.7783-2.7582a.7948.7948 0 0 0 .3927-.6813v-6.7369l2.02 1.1686a.071.071 0 0 1 .038.052v5.5826a4.504 4.504 0 0 1-4.4945 4.4944zm-9.6607-4.1254a4.4708 4.4708 0 0 1-.5346-3.0137l.1412.0852 4.783 2.7582a.7712.7712 0 0 0 .7806 0l5.8428-3.3685v2.3324a.0804.0804 0 0 1-.0332.0615L9.74 19.9502a4.4992 4.4992 0 0 1-6.1408-1.6464zM2.3408 7.8956a4.485 4.485 0 0 1 2.3655-1.9723V11.6a.7664.7664 0 0 0 .3879.6765l5.8144 3.3543-2.0201 1.1685a.0757.0757 0 0 1-.071 0l-4.8303-2.7875A4.504 4.504 0 0 1 2.3408 7.8956zm16.5963 3.8558L13.1038 8.364 15.1192 7.2a.0757.0757 0 0 1 .071 0l4.8303 2.7913a4.4944 4.4944 0 0 1-.6765 8.1042v-5.6772a.79.79 0 0 0-.407-.667zm2.0107-3.0231l-.1419.0805-4.7833 2.7582a.7759.7759 0 0 0-.7806 0L9.409 9.2V6.8676a.0762.0762 0 0 1 .0283-.0616l4.8303-2.7872a4.4992 4.4992 0 0 1 6.6802 4.66zM8.3065 12.863l-2.02-1.1638a.0804.0804 0 0 1-.038-.0567V6.0742a4.4992 4.4992 0 0 1 7.3757-3.4537l-.142.0805L8.704 5.459a.7948.7948 0 0 0-.3927.6813zm1.0976-2.3654l2.602-1.4998 2.6069 1.4998v2.9994l-2.5974 1.4997-2.6067-1.4997Z"
      />
    </svg>

    <!-- Cursor（Simple Icons 官方路径，深底白标） -->
    <svg v-else-if="kind === 'cursor'" viewBox="0 0 24 24" aria-hidden="true">
      <rect width="24" height="24" rx="5" fill="#141414" />
      <path
        fill="#fff"
        transform="translate(3.2 3.2) scale(0.733)"
        d="M11.503.131 1.891 5.678a.84.84 0 0 0-.42.726v11.188c0 .3.162.575.42.724l9.609 5.55a1 1 0 0 0 .998 0l9.61-5.55a.84.84 0 0 0 .42-.724V6.404a.84.84 0 0 0-.42-.726L12.497.131a1.01 1.01 0 0 0-.996 0M2.657 6.338h18.55c.263 0 .43.287.297.515L12.23 22.918c-.062.107-.229.064-.229-.06V12.335a.59.59 0 0 0-.295-.51l-9.11-5.257c-.109-.063-.064-.23.061-.23"
      />
    </svg>

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
}
.provider-icon.codex {
  background: #f9fafb;
}
.provider-icon.cursor {
  background: transparent;
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
.custom-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.fallback-letter {
  font-size: 11px;
  font-weight: 700;
  color: #374151;
  line-height: 1;
}
</style>
