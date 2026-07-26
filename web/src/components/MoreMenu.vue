<script setup lang="ts">
/**
 * 行内「⋯」更多菜单：Teleport 到 body，避免被表格 overflow 裁切；靠近底部时向上展开。
 */
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'

const open = defineModel<boolean>('open', { default: false })

const root = ref<HTMLElement | null>(null)
const btn = ref<HTMLElement | null>(null)
const menuEl = ref<HTMLElement | null>(null)
const pos = ref({ top: 0, left: 0, openUp: false })

const menuStyle = computed(() => {
  if (pos.value.openUp) {
    return {
      top: `${pos.value.top}px`,
      left: `${pos.value.left}px`,
      transform: 'translateY(-100%)',
    }
  }
  return {
    top: `${pos.value.top}px`,
    left: `${pos.value.left}px`,
  }
})

function placeMenu() {
  const el = btn.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const menuHeight = menuEl.value?.offsetHeight || 96
  const gap = 4
  const spaceBelow = window.innerHeight - rect.bottom
  const openUp = spaceBelow < menuHeight + gap + 8 && rect.top > menuHeight + gap
  const left = Math.min(
    Math.max(8, rect.right - 140),
    window.innerWidth - 148,
  )
  pos.value = {
    top: openUp ? rect.top - gap : rect.bottom + gap,
    left,
    openUp,
  }
}

async function toggle(ev: MouseEvent) {
  ev.stopPropagation()
  open.value = !open.value
  if (open.value) {
    await nextTick()
    placeMenu()
    await nextTick()
    placeMenu()
  }
}

function onDocClick(ev: MouseEvent) {
  if (!open.value) return
  const t = ev.target as Node
  if (root.value?.contains(t) || menuEl.value?.contains(t)) return
  open.value = false
}

function onReposition() {
  if (open.value) placeMenu()
}

watch(open, async (v) => {
  if (v) {
    await nextTick()
    placeMenu()
  }
})

onMounted(() => {
  document.addEventListener('click', onDocClick)
  window.addEventListener('resize', onReposition)
  window.addEventListener('scroll', onReposition, true)
})
onUnmounted(() => {
  document.removeEventListener('click', onDocClick)
  window.removeEventListener('resize', onReposition)
  window.removeEventListener('scroll', onReposition, true)
})

async function close() {
  open.value = false
  await nextTick()
}

defineExpose({ close })
</script>

<template>
  <div ref="root" class="more" @click.stop>
    <button
      ref="btn"
      type="button"
      class="more-btn"
      aria-label="更多操作"
      :aria-expanded="open"
      @click="toggle"
    >⋯</button>
    <Teleport to="body">
      <div
        v-if="open"
        ref="menuEl"
        class="more-menu"
        role="menu"
        :style="menuStyle"
      >
        <slot :close="close" />
      </div>
    </Teleport>
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
.more-btn:hover,
.more-btn[aria-expanded='true'] {
  background: #f3f4f6;
  border-color: var(--border, #e5e7eb);
  color: var(--text, #111827);
}
</style>

<style>
/* Teleport 到 body，需非 scoped */
.more-menu {
  position: fixed;
  z-index: 2000;
  min-width: 140px;
  background: #fff;
  border: 1px solid var(--border, #e5e7eb);
  border-radius: 10px;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.12);
  padding: 4px;
}
.more-menu button {
  display: flex;
  align-items: center;
  gap: 8px;
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
.more-menu button:hover {
  background: #f3f4f6;
}
.more-menu button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.more-menu button.danger {
  color: #b42318;
}
.more-menu button.danger:hover {
  background: #fef2f2;
}
</style>
