<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { apiFetch } from '@/lib/api'
import { getHostBridge } from '@/lib/hostBridge'

type Runtime = {
  id: string
  provider: string
  status: string
  hostName?: string
  lastHeartbeatAt?: string
}

const PROVIDERS = [
  { value: 'cursor', label: 'Cursor' },
  { value: 'claude_code', label: 'Claude Code' },
  { value: 'codex', label: 'Codex' },
  { value: 'stub', label: 'Stub（本机冒烟，无需 CLI）' },
]

const runtimes = ref<Runtime[]>([])
const provider = ref('cursor')
const err = ref('')
const okMsg = ref('')
const adding = ref(false)
let timer: number | undefined

async function load() {
  runtimes.value = await apiFetch('/api/runtimes')
}

/**
 * 手动添加运行时：经 Desktop HostBridge 调 CLI 探测本机是否安装；
 * 未安装则 CLI 返回非 0，界面提示注册错误。
 */
async function addRuntime() {
  err.value = ''
  okMsg.value = ''
  adding.value = true
  try {
    const res = await getHostBridge().addRuntime(provider.value)
    if (!res.ok) {
      err.value = res.message || '注册失败'
      return
    }
    okMsg.value = res.message || '已添加'
    await load()
  } catch (e) {
    err.value = e instanceof Error ? e.message : '添加失败'
  } finally {
    adding.value = false
  }
}

async function removeRuntime(r: Runtime) {
  err.value = ''
  try {
    await getHostBridge().removeRuntime(r.provider)
    await apiFetch(`/api/runtimes/${r.id}`, { method: 'DELETE' })
    await load()
  } catch (e) {
    err.value = e instanceof Error ? e.message : '移除失败'
  }
}

onMounted(() => {
  load()
  // 轮询只刷新「已添加」项的在线/心跳，不负责发现本机全部 CLI
  timer = window.setInterval(load, 5000)
})
onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <section class="page">
    <header class="page-header"><h2>运行时</h2></header>
    <p class="muted">
      本机安装了 Cursor / Claude Code / Codex 也不会自动出现。须<strong>手动添加</strong>后才会探测并注册；
      未安装对应 CLI 时添加会失败。轮询仅用于刷新已添加项是否在线。
    </p>

    <div class="panel">
      <label>
        添加运行时
        <select v-model="provider">
          <option v-for="p in PROVIDERS" :key="p.value" :value="p.value">{{ p.label }}</option>
        </select>
      </label>
      <button type="button" class="primary" :disabled="adding" @click="addRuntime">
        {{ adding ? '探测并注册…' : '添加' }}
      </button>
      <p v-if="err" class="error">{{ err }}</p>
      <p v-if="okMsg" class="ok">{{ okMsg }}</p>
      <p class="muted small">
        非 Desktop 时请用 CLI：
        <code>rudder runtime add --provider cursor</code>
      </p>
    </div>

    <ul class="list">
      <li v-if="!runtimes.length" class="muted">暂无已添加的运行时</li>
      <li v-for="r in runtimes" :key="r.id">
        <div class="row">
          <div>
            <strong>{{ r.provider }}</strong>
            <span class="status" :class="r.status">{{ r.status }}</span>
            <div class="muted">{{ r.hostName || '-' }} · 心跳 {{ r.lastHeartbeatAt || '-' }}</div>
          </div>
          <button type="button" class="mini" @click="removeRuntime(r)">移除</button>
        </div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.panel {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.list {
  list-style: none;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 16px;
}
li { padding: 10px 0; border-bottom: 1px solid var(--border); }
.row { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.status {
  margin-left: 8px;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  background: #eee;
}
.status.online { background: #d1fae5; color: #065f46; }
.status.offline { background: #fee2e2; color: #991b1b; }
.mini {
  border: 1px solid var(--border);
  background: var(--bg);
  border-radius: 6px;
  padding: 6px 10px;
  cursor: pointer;
}
.ok { color: #065f46; font-size: 14px; }
.small { font-size: 12px; }
code { font-size: 12px; }
select { width: 100%; margin-top: 6px; padding: 8px; border-radius: 8px; border: 1px solid var(--border); }
</style>
