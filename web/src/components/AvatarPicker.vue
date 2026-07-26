<script setup lang="ts">
/**
 * 智能体头像选择：图库点选 / 随机换一张 / 可选自定义 URL。
 */
import { computed, ref, watch } from 'vue'
import { AGENT_AVATARS, pickRandomAvatar } from '@/lib/agentAvatars'

const props = withDefaults(
  defineProps<{
    modelValue?: string
    open?: boolean
    allowUrl?: boolean
  }>(),
  { modelValue: '', open: false, allowUrl: true },
)

const emit = defineEmits<{
  'update:modelValue': [string]
  'update:open': [boolean]
}>()

const urlDraft = ref('')
const q = ref('')

watch(
  () => props.open,
  (v) => {
    if (v) urlDraft.value = props.modelValue?.startsWith('http') ? props.modelValue : ''
  },
)

const filtered = computed(() => {
  const s = q.value.trim().toLowerCase()
  if (!s) return AGENT_AVATARS
  return AGENT_AVATARS.filter((a) => a.toLowerCase().includes(s))
})

function close() {
  emit('update:open', false)
}

function select(src: string) {
  emit('update:modelValue', src)
  close()
}

function randomize() {
  emit('update:modelValue', pickRandomAvatar(props.modelValue))
}

function applyUrl() {
  const u = urlDraft.value.trim()
  if (!u) return
  emit('update:modelValue', u)
  close()
}
</script>

<template>
  <div v-if="open" class="backdrop" @click.self="close">
    <div class="panel" role="dialog" aria-modal="true" aria-label="选择头像">
      <header class="head">
        <h3>选择头像</h3>
        <div class="head-actions">
          <button type="button" class="btn-ghost" @click="randomize">随机换一张</button>
          <button type="button" class="x" aria-label="关闭" @click="close">×</button>
        </div>
      </header>

      <label class="search">
        <input v-model="q" type="search" placeholder="筛选文件名…" />
      </label>

      <div class="grid">
        <button
          v-for="src in filtered"
          :key="src"
          type="button"
          class="cell"
          :class="{ on: modelValue === src }"
          @click="select(src)"
        >
          <img :src="src" alt="" width="56" height="56" loading="lazy" />
        </button>
      </div>

      <div v-if="allowUrl" class="url-row">
        <input v-model="urlDraft" type="url" placeholder="自定义图片 URL（https://…）" />
        <button type="button" class="btn-primary" :disabled="!urlDraft.trim()" @click="applyUrl">
          使用 URL
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 90;
  padding: 16px;
}
.panel {
  width: min(560px, 100%);
  max-height: min(80vh, 640px);
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.16);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
}
.head h3 { margin: 0; font-size: 16px; }
.head-actions { display: flex; align-items: center; gap: 8px; }
.x {
  border: none;
  background: transparent;
  font-size: 22px;
  color: var(--muted);
  cursor: pointer;
  line-height: 1;
}
.search {
  padding: 10px 16px 0;
}
.search input {
  width: 100%;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 7px 10px;
  font: inherit;
  font-size: 13px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(64px, 1fr));
  gap: 8px;
  padding: 12px 16px;
  overflow: auto;
  flex: 1;
}
.cell {
  border: 2px solid transparent;
  background: #f3f4f6;
  border-radius: 14px;
  padding: 4px;
  cursor: pointer;
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.cell:hover { background: #e5e7eb; }
.cell.on { border-color: #1c2333; background: #fff; }
.cell img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 10px;
  display: block;
}
.url-row {
  display: flex;
  gap: 8px;
  padding: 12px 16px 16px;
  border-top: 1px solid #f3f4f6;
}
.url-row input {
  flex: 1;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 8px 10px;
  font: inherit;
  font-size: 13px;
}
.btn-ghost {
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 8px;
  padding: 6px 10px;
  font-size: 12px;
  cursor: pointer;
}
.btn-primary {
  border: none;
  background: #1c2333;
  color: #fff;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}
.btn-primary:disabled { opacity: 0.45; cursor: not-allowed; }
</style>
