<script setup lang="ts">
/**
 * 应用内提示弹窗（替代 window.alert）。
 * 视觉对齐系统提示：白底圆角 + 全宽蓝色胶囊 OK。
 */
import { nextTick, onUnmounted, useTemplateRef, watch } from 'vue'

const emit = defineEmits<{
  close: []
}>()

const props = withDefaults(
  defineProps<{
    open: boolean
    title?: string
    message: string
    okLabel?: string
  }>(),
  {
    title: '',
    okLabel: 'OK',
  },
)

const okBtn = useTemplateRef<HTMLButtonElement>('okBtn')

function onKey(e: KeyboardEvent) {
  if (!props.open) return
  if (e.key === 'Escape' || e.key === 'Enter') {
    e.preventDefault()
    emit('close')
  }
}

watch(
  () => props.open,
  async (open) => {
    if (open) {
      window.addEventListener('keydown', onKey)
      await nextTick()
      okBtn.value?.focus()
    } else {
      window.removeEventListener('keydown', onKey)
    }
  },
)

onUnmounted(() => {
  window.removeEventListener('keydown', onKey)
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="alert-backdrop"
      role="presentation"
      @click.self="emit('close')"
    >
      <div
        class="alert-modal"
        role="alertdialog"
        aria-modal="true"
        :aria-label="title || '提示'"
      >
        <h3 v-if="title" class="alert-title">{{ title }}</h3>
        <p class="alert-message">{{ message }}</p>
        <button ref="okBtn" type="button" class="alert-ok" @click="emit('close')">
          {{ okLabel }}
        </button>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.alert-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.28);
}
.alert-modal {
  width: min(320px, 100%);
  background: #fff;
  border-radius: 14px;
  padding: 20px 16px 14px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.22);
  text-align: center;
  outline: none;
}
.alert-title {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 650;
  color: #111827;
}
.alert-message {
  margin: 0 0 16px;
  font-size: 13px;
  line-height: 1.5;
  color: #1f2937;
  white-space: pre-wrap;
  word-break: break-word;
}
/* 与系统 alert 一致：全宽蓝色胶囊 OK，勿改形状/配色 */
.alert-ok {
  display: block;
  width: 100%;
  border: none;
  border-radius: 999px;
  padding: 10px 16px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.01em;
  color: #fff;
  background: #007aff;
  cursor: pointer;
  -webkit-appearance: none;
  appearance: none;
}
.alert-ok:hover {
  background: #0066d6;
}
.alert-ok:active {
  background: #005bbf;
}
</style>
