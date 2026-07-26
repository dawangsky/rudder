<script setup lang="ts">
/**
 * 运行时列表「智能体」列：并列头像，悬停展示详情卡片。
 */
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ProviderIcon from '@/components/ProviderIcon.vue'
import {
  agentDetailStatus,
  ownerDisplayName,
  type Agent,
} from '@/lib/agents'
import { getCustomProviderIcon } from '@/lib/providerIcons'
import { displayName, type Runtime } from '@/lib/runtimes'
import { getSessionEmail } from '@/lib/session'

const props = withDefaults(
  defineProps<{
    runtime: Runtime
    agents: Agent[]
    maxVisible?: number
  }>(),
  { maxVisible: 5 },
)

const router = useRouter()
const hoverId = ref('')
const anchor = ref<HTMLElement | null>(null)
const tipEl = ref<HTMLElement | null>(null)
const tipPos = ref({ top: 0, left: 0 })
let hideTimer: number | undefined

const bound = computed(() =>
  props.agents.filter(
    (a) => a.runtimeId === props.runtime.id && (a.status || '').toLowerCase() !== 'archived',
  ),
)

const visible = computed(() => bound.value.slice(0, props.maxVisible))
const extra = computed(() => Math.max(0, bound.value.length - props.maxVisible))

const hoverAgent = computed(() => bound.value.find((a) => a.id === hoverId.value) || null)

const runtimeOnline = computed(() => props.runtime.status === 'online')

const ownerName = computed(() => ownerDisplayName(getSessionEmail()))

const runtimeLabel = computed(() => {
  const r = props.runtime
  const host = r.hostName || ''
  return host ? `${displayName(r)} (${host})` : displayName(r)
})

function statusText(a: Agent) {
  return agentDetailStatus(a.status, runtimeOnline.value)
}

function statusOn(a: Agent) {
  return statusText(a).startsWith('在线')
}

function placeTip() {
  const el = anchor.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const tipW = tipEl.value?.offsetWidth || 260
  const tipH = tipEl.value?.offsetHeight || 160
  let left = rect.left + rect.width / 2 - tipW / 2
  left = Math.max(8, Math.min(left, window.innerWidth - tipW - 8))
  let top = rect.bottom + 8
  if (top + tipH > window.innerHeight - 8 && rect.top > tipH + 8) {
    top = rect.top - tipH - 8
  }
  tipPos.value = { top, left }
}

function onEnter(a: Agent, ev: MouseEvent) {
  if (hideTimer) {
    window.clearTimeout(hideTimer)
    hideTimer = undefined
  }
  hoverId.value = a.id
  anchor.value = ev.currentTarget as HTMLElement
  nextTick(() => {
    placeTip()
    nextTick(placeTip)
  })
}

function onLeave() {
  hideTimer = window.setTimeout(() => {
    hoverId.value = ''
    anchor.value = null
  }, 120)
}

function onTipEnter() {
  if (hideTimer) {
    window.clearTimeout(hideTimer)
    hideTimer = undefined
  }
}

function onTipLeave() {
  onLeave()
}

function goAgent(a: Agent) {
  router.push({ name: 'agent-detail', params: { agentId: a.id } })
}

function onScroll() {
  if (hoverId.value) placeTip()
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, true)
  window.addEventListener('resize', onScroll)
})
onUnmounted(() => {
  window.removeEventListener('scroll', onScroll, true)
  window.removeEventListener('resize', onScroll)
  if (hideTimer) window.clearTimeout(hideTimer)
})
</script>

<template>
  <div v-if="bound.length" class="avatars" @click.stop>
    <button
      v-for="a in visible"
      :key="a.id"
      type="button"
      class="av"
      :class="{ on: hoverId === a.id }"
      :title="a.name"
      @mouseenter="onEnter(a, $event)"
      @mouseleave="onLeave"
      @click="goAgent(a)"
    >
      <ProviderIcon
        :provider="a.provider"
        :custom-src="getCustomProviderIcon(runtime.daemonId, a.provider)"
        :size="22"
      />
    </button>
    <span v-if="extra > 0" class="more">+{{ extra }}</span>

    <Teleport to="body">
      <div
        v-if="hoverAgent"
        ref="tipEl"
        class="agent-tip"
        :style="{ top: `${tipPos.top}px`, left: `${tipPos.left}px` }"
        @mouseenter="onTipEnter"
        @mouseleave="onTipLeave"
      >
        <div class="tip-head">
          <span class="tip-av">
            <ProviderIcon
              :provider="hoverAgent.provider"
              :custom-src="getCustomProviderIcon(runtime.daemonId, hoverAgent.provider)"
              :size="28"
            />
          </span>
          <div class="tip-title">
            <div class="name-row">
              <strong>{{ hoverAgent.name }}</strong>
              <span class="lock" title="私有" aria-hidden="true">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none">
                  <rect x="5" y="11" width="14" height="10" rx="2" stroke="currentColor" stroke-width="1.6" />
                  <path d="M8 11V8a4 4 0 0 1 8 0v3" stroke="currentColor" stroke-width="1.6" />
                </svg>
              </span>
            </div>
            <span class="tip-status" :class="{ on: statusOn(hoverAgent) }">
              <i class="dot" />{{ statusText(hoverAgent) }}
            </span>
          </div>
        </div>
        <dl class="tip-meta">
          <div>
            <dt>运行时</dt>
            <dd>
              <span class="rt">
                <i class="sig" :class="{ on: runtimeOnline }" />
                {{ runtimeLabel }}
              </span>
            </dd>
          </div>
          <div>
            <dt>模型</dt>
            <dd>运行时默认</dd>
          </div>
          <div>
            <dt>所有者</dt>
            <dd>{{ ownerName }}</dd>
          </div>
        </dl>
      </div>
    </Teleport>
  </div>
  <span v-else class="empty-cell">—</span>
</template>

<style scoped>
.avatars {
  display: inline-flex;
  align-items: center;
  gap: 0;
  min-height: 28px;
}
.av {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 2px solid #fff;
  background: #f3f4f6;
  padding: 0;
  margin-left: -6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 0 0 1px #e5e7eb;
  position: relative;
  z-index: 1;
}
.av:first-child { margin-left: 0; }
.av:hover,
.av.on {
  z-index: 2;
  box-shadow: 0 0 0 2px #93c5fd;
}
.more {
  margin-left: 6px;
  font-size: 12px;
  color: var(--muted, #6b7280);
  font-weight: 600;
}
.empty-cell { color: var(--muted, #6b7280); }
</style>

<style>
.agent-tip {
  position: fixed;
  z-index: 2100;
  width: 260px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.14);
  padding: 12px 14px;
  pointer-events: auto;
}
.agent-tip .tip-head {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 12px;
}
.agent-tip .tip-av {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  background: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.agent-tip .name-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.agent-tip .name-row strong {
  font-size: 14px;
  font-weight: 650;
  color: #111827;
}
.agent-tip .lock { color: #9ca3af; display: inline-flex; }
.agent-tip .tip-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  font-size: 12px;
  color: #6b7280;
}
.agent-tip .tip-status.on { color: #059669; }
.agent-tip .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #d1d5db;
  display: inline-block;
}
.agent-tip .tip-status.on .dot { background: #10b981; }
.agent-tip .tip-meta {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.agent-tip .tip-meta > div {
  display: grid;
  grid-template-columns: 52px 1fr;
  gap: 8px;
  font-size: 12px;
}
.agent-tip dt {
  margin: 0;
  color: #9ca3af;
}
.agent-tip dd {
  margin: 0;
  color: #374151;
}
.agent-tip .rt {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.agent-tip .sig {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d1d5db;
  display: inline-block;
}
.agent-tip .sig.on { background: #10b981; }
</style>
