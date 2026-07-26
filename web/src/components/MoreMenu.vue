<script setup lang="ts">
/**
 * 行内「⋯」更多菜单：点击展开，点外部或选中项后关闭。
 */
import { nextTick, onMounted, onUnmounted, ref } from 'vue'

const open = defineModel<boolean>('open', { default: false })

const root = ref<HTMLElement | null>(null)

function toggle(ev: MouseEvent) {
  ev.stopPropagation()
  open.value = !open.value
}

function onDocClick(ev: MouseEvent) {
  if (!open.value) return
  const el = root.value
  if (el && !el.contains(ev.target as Node)) {
    open.value = false
  }
}

onMounted(() => document.addEventListener('click', onDocClick))
onUnmounted(() => document.removeEventListener('click', onDocClick))

async function close() {
  open.value = false
  await nextTick()
}

defineExpose({ close })
</script>

<template>
  <div ref="root" class="more" @click.stop>
    <button
      type="button"
      class="more-btn"
      aria-label="更多操作"
      :aria-expanded="open"
      @click="toggle"
    >⋯</button>
    <div v-if="open" class="more-menu" role="menu">
      <slot :close="close" />
    </div>
  </div>
</template>

<style scoped>
.more {
  position: relative;
  display: inline-flex;
  justify-content: flex-end;
}
.more-btn {
  border: 1px solid transparent;
  background: transparent;
  border-radius: 8px;
  width: 32px;
  height: 32px;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  color: var(--muted, #6b7280);
}
.more-btn:hover {
  background: #f3f4f6;
  border-color: var(--border, #e5e7eb);
  color: var(--text, #111827);
}
.more-menu {
  position: absolute;
  right: 0;
  top: calc(100% + 4px);
  min-width: 132px;
  background: #fff;
  border: 1px solid var(--border, #e5e7eb);
  border-radius: 10px;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.12);
  padding: 4px;
  z-index: 30;
}
.more-menu :deep(button) {
  display: block;
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text, #111827);
}
.more-menu :deep(button:hover) {
  background: #f3f4f6;
}
.more-menu :deep(button:disabled) {
  opacity: 0.45;
  cursor: not-allowed;
}
.more-menu :deep(button.danger) {
  color: #b42318;
}
.more-menu :deep(button.danger:hover) {
  background: #fef2f2;
}
</style>
