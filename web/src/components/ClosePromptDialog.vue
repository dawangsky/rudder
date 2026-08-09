<script setup lang="ts">
/**
 * Desktop 关闭确认：最小化到托盘 / 退出程序，可选「不再提示」。
 */
import { onMounted, onUnmounted, ref } from 'vue'
import { getHostBridge, isDesktopHost } from '@/lib/hostBridge'

const open = ref(false)
/** 勾选后记住本次选择，下次不再询问 */
const rememberChoice = ref(false)
let unsub: (() => void) | undefined

function show() {
  rememberChoice.value = false
  open.value = true
}

function decide(action: 'quit' | 'minimize' | 'cancel') {
  open.value = false
  void getHostBridge().resolveClosePrompt({
    action,
    askEveryTime: !rememberChoice.value,
  })
}

onMounted(() => {
  if (!isDesktopHost()) return
  unsub = getHostBridge().onClosePrompt(() => show())
})

onUnmounted(() => {
  unsub?.()
})
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="backdrop" role="presentation" @click.self="decide('cancel')">
      <div
        class="dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="close-prompt-title"
      >
        <h2 id="close-prompt-title">关闭窗口后如何处理？</h2>
        <p class="lead">可最小化到系统托盘在后台运行，或直接退出程序。</p>

        <label class="remember">
          <input v-model="rememberChoice" type="checkbox" />
          <span>不再提示，记住本次选择</span>
        </label>

        <div class="acts">
          <button type="button" class="btn primary" @click="decide('minimize')">
            最小化到托盘
          </button>
          <button type="button" class="btn ghost" @click="decide('quit')">退出程序</button>
          <button type="button" class="btn ghost" @click="decide('cancel')">取消</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.backdrop {
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(28, 35, 51, 0.4);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
}

.dialog {
  width: min(420px, 100%);
  background: var(--panel, #fff);
  border: 1px solid var(--border, #e2e5eb);
  border-radius: 14px;
  padding: 22px 22px 18px;
  box-shadow: 0 16px 40px rgba(16, 24, 40, 0.14);
}

h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  line-height: 1.35;
  color: var(--text, #1c2333);
}

.lead {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--muted, #6b7280);
}

.remember {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 18px 0 20px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text, #1c2333);
  cursor: pointer;
  user-select: none;
}

.remember input {
  width: 15px;
  height: 15px;
  accent-color: var(--accent, #0f6e56);
  cursor: pointer;
}

.acts {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.btn {
  appearance: none;
  border-radius: 8px;
  padding: 8px 14px;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.12s ease, border-color 0.12s ease, filter 0.12s ease;
}

.btn.primary {
  border: none;
  background: var(--text, #1c2333);
  color: #fff;
}

.btn.primary:hover {
  filter: brightness(1.08);
}

.btn.ghost {
  border: 1px solid var(--border, #e2e5eb);
  background: var(--panel, #fff);
  color: var(--text, #1c2333);
}

.btn.ghost:hover {
  background: #f6f7f9;
}
</style>
