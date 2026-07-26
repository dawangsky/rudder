<script setup lang="ts">
/**
 * 应用内确认弹窗（替代 window.confirm），风格与运行时/智能体删除弹窗一致。
 */
const emit = defineEmits<{
  cancel: []
  confirm: []
}>()

const ack = defineModel<boolean>('ack', { default: false })

withDefaults(
  defineProps<{
    open: boolean
    title: string
    description?: string
    confirmLabel?: string
    cancelLabel?: string
    tone?: 'danger' | 'warn' | 'default'
    busy?: boolean
    requireAck?: boolean
    ackLabel?: string
    callout?: string
    calloutTone?: 'danger' | 'warn'
  }>(),
  {
    description: '',
    confirmLabel: '确认',
    cancelLabel: '取消',
    tone: 'default',
    busy: false,
    requireAck: false,
    ackLabel: '我已了解风险。',
    callout: '',
    calloutTone: 'warn',
  },
)
</script>

<template>
  <div
    v-if="open"
    class="modal-backdrop"
    @click.self="!busy && emit('cancel')"
  >
    <div class="modal" role="dialog" aria-modal="true">
      <h3>{{ title }}</h3>
      <p v-if="description">{{ description }}</p>
      <div v-if="callout" class="callout" :class="calloutTone">{{ callout }}</div>
      <label v-if="requireAck" class="ack">
        <input v-model="ack" type="checkbox" :disabled="busy" />
        <span>{{ ackLabel }}</span>
      </label>
      <slot />
      <div class="modal-actions">
        <button
          type="button"
          class="btn-ghost"
          :disabled="busy"
          @click="emit('cancel')"
        >{{ cancelLabel }}</button>
        <button
          type="button"
          class="btn-confirm"
          :class="tone"
          :disabled="busy || (requireAck && !ack)"
          @click="emit('confirm')"
        >
          {{ busy ? '处理中…' : confirmLabel }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
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
  margin: 0 0 12px;
  font-size: 14px;
  line-height: 1.55;
  color: #374151;
  white-space: pre-line;
}
.callout {
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.45;
  margin-bottom: 12px;
}
.callout.warn {
  background: #fffbeb;
  border: 1px solid #f6d98a;
  color: #78350f;
}
.callout.danger {
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #991b1b;
}
.ack {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  font-size: 13px;
  line-height: 1.45;
  margin-bottom: 12px;
  cursor: pointer;
  color: #374151;
}
.ack input { margin-top: 2px; }
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 4px;
}
.btn-ghost {
  border: 1px solid var(--border, #e5e7eb);
  background: #fff;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  cursor: pointer;
  color: var(--text, #111827);
}
.btn-ghost:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-confirm {
  border: none;
  border-radius: 8px;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.btn-confirm:disabled { opacity: 0.45; cursor: not-allowed; }
.btn-confirm.default {
  background: #111827;
  color: #fff;
}
.btn-confirm.warn {
  background: #fffbeb;
  color: #92400e;
  border: 1px solid #f6d98a;
}
.btn-confirm.danger {
  background: #fce8e6;
  color: #b42318;
}
</style>
