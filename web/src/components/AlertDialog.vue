<script setup lang="ts">
/**
 * 应用内提示弹窗（替代 window.alert），风格与 ConfirmDialog 一致。
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
    title: '无法完成操作',
    okLabel: '知道了',
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
      class="modal-backdrop"
      role="presentation"
      @click.self="emit('close')"
    >
      <div
        class="modal"
        role="alertdialog"
        aria-modal="true"
        :aria-labelledby="title ? 'alert-dialog-title' : undefined"
        :aria-describedby="'alert-dialog-desc'"
      >
        <h3 v-if="title" id="alert-dialog-title">{{ title }}</h3>
        <p id="alert-dialog-desc">{{ message }}</p>
        <div class="modal-actions">
          <button ref="okBtn" type="button" class="btn-confirm" @click="emit('close')">
            {{ okLabel }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1100;
  padding: 24px;
}
.modal {
  width: min(420px, 100%);
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.18);
}
.modal h3 {
  margin: 0 0 8px;
  font-size: 17px;
  font-weight: 650;
  color: var(--text, #111827);
}
.modal > p {
  margin: 0 0 16px;
  font-size: 14px;
  line-height: 1.55;
  color: #374151;
  white-space: pre-line;
  word-break: break-word;
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.btn-confirm {
  border: none;
  border-radius: 8px;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  background: #111827;
  color: #fff;
}
.btn-confirm:hover {
  background: #1f2937;
}
</style>
