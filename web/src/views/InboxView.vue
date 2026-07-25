<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { apiFetch } from '@/lib/api'

type InboxItem = {
  id: string
  title: string
  body: string
  read: boolean
  createdAt?: string
}

const items = ref<InboxItem[]>([])
const unread = ref(0)
const selectedId = ref<string | null>(null)
let timer: number | undefined

const selected = computed(() => items.value.find((i) => i.id === selectedId.value) || null)

function formatTime(iso?: string) {
  if (!iso) return ''
  const t = Date.parse(iso)
  if (Number.isNaN(t)) return iso
  const sec = Math.max(0, Math.floor((Date.now() - t) / 1000))
  if (sec < 60) return '刚刚'
  const min = Math.floor(sec / 60)
  if (min < 60) return `${min} 分钟前`
  const hr = Math.floor(min / 60)
  if (hr < 24) return `${hr} 小时前`
  return new Date(t).toLocaleString()
}

async function load() {
  const data = await apiFetch<{ items: InboxItem[]; unread: number }>('/api/inbox')
  items.value = data.items || []
  unread.value = data.unread || 0
  if (selectedId.value && !items.value.some((i) => i.id === selectedId.value)) {
    selectedId.value = null
  }
}

async function selectItem(item: InboxItem) {
  selectedId.value = item.id
  if (!item.read) {
    try {
      await apiFetch(`/api/inbox/${item.id}/read`, { method: 'POST', body: '{}' })
      item.read = true
      unread.value = Math.max(0, unread.value - 1)
    } catch {
      /* ignore */
    }
  }
}

onMounted(() => {
  load()
  timer = window.setInterval(load, 15000)
})
onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <section class="inbox-shell">
    <!-- 中栏：通知列表 -->
    <aside class="list-pane">
      <header class="list-header">
        <h2>收件箱</h2>
        <button type="button" class="icon-btn" title="更多" aria-label="更多">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle cx="5" cy="12" r="1.5" fill="currentColor" />
            <circle cx="12" cy="12" r="1.5" fill="currentColor" />
            <circle cx="19" cy="12" r="1.5" fill="currentColor" />
          </svg>
        </button>
      </header>

      <div v-if="!items.length" class="empty list-empty">
        <svg class="empty-icon" width="40" height="40" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path
            d="M4 8.5h16v9.5a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8.5Z"
            stroke="currentColor"
            stroke-width="1.5"
          />
          <path
            d="M4 8.5 6.5 4h11L20 8.5M4 8.5h16"
            stroke="currentColor"
            stroke-width="1.5"
            stroke-linejoin="round"
          />
          <path d="M9 13h6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
        </svg>
        <p>暂无通知</p>
      </div>

      <ul v-else class="item-list">
        <li
          v-for="i in items"
          :key="i.id"
          class="item"
          :class="{ active: selectedId === i.id, unread: !i.read }"
          @click="selectItem(i)"
        >
          <div class="item-top">
            <span class="item-title">{{ i.title }}</span>
            <span class="item-time">{{ formatTime(i.createdAt) }}</span>
          </div>
          <p class="item-body">{{ i.body || '—' }}</p>
        </li>
      </ul>
    </aside>

    <!-- 右栏：详情 -->
    <main class="detail-pane">
      <div v-if="!selected" class="empty detail-empty">
        <svg class="empty-icon lg" width="56" height="56" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path
            d="M4 8.5h16v9.5a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8.5Z"
            stroke="currentColor"
            stroke-width="1.4"
          />
          <path
            d="M4 8.5 6.5 4h11L20 8.5M4 8.5h16"
            stroke="currentColor"
            stroke-width="1.4"
            stroke-linejoin="round"
          />
          <path d="M9 13h6" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
        </svg>
        <p>{{ items.length ? '选择一条通知查看详情' : '收件箱为空' }}</p>
      </div>

      <article v-else class="detail">
        <header class="detail-header">
          <h3>{{ selected.title }}</h3>
          <span class="muted">{{ formatTime(selected.createdAt) }}</span>
        </header>
        <div class="detail-body">
          {{ selected.body || '（无正文）' }}
        </div>
      </article>
    </main>
  </section>
</template>

<style scoped>
.inbox-shell {
  display: flex;
  margin: -24px;
  height: calc(100% + 48px);
  min-height: 520px;
  background: var(--panel);
  flex: 1;
}

.list-pane {
  width: 320px;
  flex-shrink: 0;
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  background: var(--panel);
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 12px;
  border-bottom: 1px solid transparent;
}
.list-header h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.icon-btn {
  border: none;
  background: transparent;
  color: var(--muted);
  padding: 4px;
  border-radius: 6px;
  cursor: pointer;
  line-height: 0;
}
.icon-btn:hover {
  background: var(--bg);
  color: var(--text);
}

.item-list {
  list-style: none;
  margin: 0;
  padding: 4px 8px 16px;
  overflow: auto;
  flex: 1;
}

.item {
  padding: 12px 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 2px;
}
.item:hover {
  background: var(--bg);
}
.item.active {
  background: var(--bg);
}
.item.unread .item-title {
  font-weight: 700;
}
.item.unread::before {
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
  margin-right: 6px;
  vertical-align: middle;
}

.item-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}
.item-title {
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.item-time {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--muted);
}
.item-body {
  margin: 0;
  font-size: 13px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-pane {
  flex: 1;
  min-width: 0;
  background: var(--bg);
  display: flex;
  flex-direction: column;
}

.empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--muted);
  padding: 32px 16px;
}
.empty p {
  margin: 0;
  font-size: 14px;
}
.empty-icon {
  color: #c4c9d4;
}
.empty-icon.lg {
  color: #c4c9d4;
}
.list-empty {
  min-height: 240px;
}
.detail-empty {
  min-height: 100%;
}

.detail {
  padding: 28px 36px;
  max-width: 720px;
}
.detail-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border);
}
.detail-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}
.detail-body {
  font-size: 14px;
  line-height: 1.65;
  color: var(--text);
  white-space: pre-wrap;
}

@media (max-width: 800px) {
  .list-pane {
    width: 260px;
  }
  .detail {
    padding: 20px;
  }
}
</style>
