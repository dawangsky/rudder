<script setup lang="ts">
/**
 * Skill 详情：概览编辑、使用情况、SKILL.md 预览。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiFetch } from '@/lib/api'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import AgentAvatar from '@/components/AgentAvatar.vue'
import {
  formatSkillDisplayPath,
  formatSkillTime,
  sourceLabel,
  type Skill,
} from '@/lib/skills'
import type { Agent } from '@/lib/agents'

type Tab = 'overview' | 'files'

const route = useRoute()
const router = useRouter()

const skill = ref<Skill | null>(null)
const agents = ref<Agent[]>([])
const loading = ref(false)
const busy = ref(false)
const err = ref('')
const okMsg = ref('')
const tab = ref<Tab>('overview')

const editName = ref('')
const editDescription = ref('')
const editContent = ref('')
const dirty = ref(false)

const pendingDelete = ref(false)
const showBind = ref(false)
const bindSelected = ref<string[]>([])

const skillId = computed(() => String(route.params.skillId || ''))

const usedAgents = computed(() => skill.value?.agents || [])

const fileCount = computed(() => {
  if (!skill.value) return 1
  // 本轮不做多文件包；runtime 来源仍显示 1（正文）
  return 1
})

const sourceHint = computed(() => {
  const s = skill.value
  if (!s) return ''
  if (s.sourceType === 'runtime' && s.sourceRef) {
    return `本地路径 · ${formatSkillDisplayPath(s.sourceRef)}`
  }
  if (s.sourceType === 'url' && s.sourceRef) return `URL · ${s.sourceRef}`
  return sourceLabel(s.sourceType)
})

async function load() {
  if (!skillId.value) return
  loading.value = true
  err.value = ''
  try {
    const [s, a] = await Promise.all([
      apiFetch<Skill>(`/api/skills/${skillId.value}`),
      apiFetch<Agent[]>('/api/agents'),
    ])
    skill.value = s
    agents.value = a.filter((x) => (x.status || '').toLowerCase() !== 'archived')
    editName.value = s.name
    editDescription.value = s.description || ''
    editContent.value = s.content || ''
    dirty.value = false
  } catch (e) {
    err.value = e instanceof Error ? e.message : '加载失败'
    skill.value = null
  } finally {
    loading.value = false
  }
}

function markDirty() {
  dirty.value = true
  okMsg.value = ''
}

async function saveOverview() {
  if (!skill.value) return
  const name = editName.value.trim()
  if (!name) {
    err.value = '名称不能为空'
    return
  }
  busy.value = true
  err.value = ''
  okMsg.value = ''
  try {
    skill.value = await apiFetch<Skill>(`/api/skills/${skill.value.id}`, {
      method: 'PATCH',
      body: JSON.stringify({
        name,
        description: editDescription.value.trim(),
      }),
    })
    editName.value = skill.value.name
    editDescription.value = skill.value.description || ''
    editContent.value = skill.value.content || ''
    dirty.value = false
    okMsg.value = '已保存'
  } catch (e) {
    err.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    busy.value = false
  }
}

async function saveContent() {
  if (!skill.value) return
  busy.value = true
  err.value = ''
  okMsg.value = ''
  try {
    skill.value = await apiFetch<Skill>(`/api/skills/${skill.value.id}`, {
      method: 'PATCH',
      body: JSON.stringify({ content: editContent.value }),
    })
    editName.value = skill.value.name
    editDescription.value = skill.value.description || ''
    editContent.value = skill.value.content || ''
    dirty.value = false
    okMsg.value = '已保存 SKILL.md'
  } catch (e) {
    err.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    busy.value = false
  }
}

function openBind() {
  bindSelected.value = []
  showBind.value = true
}

function toggleBind(id: string) {
  const set = new Set(bindSelected.value)
  if (set.has(id)) set.delete(id)
  else set.add(id)
  bindSelected.value = [...set]
}

async function confirmBind() {
  if (!skill.value || !bindSelected.value.length) return
  busy.value = true
  err.value = ''
  try {
    await apiFetch('/api/skills/bind-agents', {
      method: 'POST',
      body: JSON.stringify({
        skillIds: [skill.value.id],
        agentIds: bindSelected.value,
      }),
    })
    showBind.value = false
    await load()
    okMsg.value = '已添加到智能体'
  } catch (e) {
    err.value = e instanceof Error ? e.message : '添加失败'
  } finally {
    busy.value = false
  }
}

async function confirmDelete() {
  if (!skill.value) return
  busy.value = true
  err.value = ''
  try {
    await apiFetch(`/api/skills/${skill.value.id}`, { method: 'DELETE' })
    pendingDelete.value = false
    await router.replace({ name: 'skills' })
  } catch (e) {
    err.value = e instanceof Error ? e.message : '删除失败'
  } finally {
    busy.value = false
  }
}

function goAgent(id: string) {
  router.push({ name: 'agent-detail', params: { agentId: id } })
}

watch(skillId, () => void load())
onMounted(load)
</script>

<template>
  <section class="page">
    <div v-if="loading" class="muted pad">加载中…</div>
    <template v-else-if="skill">
      <header class="top">
        <nav class="crumbs">
          <router-link :to="{ name: 'skills' }">Skills</router-link>
          <span class="sep">›</span>
          <span>{{ skill.name }}</span>
        </nav>
        <div class="top-actions">
          <button type="button" class="btn-add" :disabled="busy" @click="openBind">+ 添加到智能体</button>
          <button type="button" class="icon-btn danger" title="删除" aria-label="删除" @click="pendingDelete = true">
            ⌫
          </button>
        </div>
      </header>

      <div class="title-row">
        <span class="skill-ico" aria-hidden="true">▦</span>
        <div class="title-main">
          <h1>{{ skill.name }}</h1>
          <div class="meta">
            <span>{{ sourceHint }}</span>
            <span>{{ fileCount }} 个文件</span>
            <span>{{ skill.agentCount || 0 }} 个智能体在用</span>
            <span>
              {{ skill.createdBy || '未知' }} 于 {{ formatSkillTime(skill.updatedAt || skill.createdAt) }}更新
            </span>
          </div>
        </div>
      </div>

      <div class="tabs">
        <button type="button" :class="{ on: tab === 'overview' }" @click="tab = 'overview'">概览</button>
        <button type="button" :class="{ on: tab === 'files' }" @click="tab = 'files'">
          文件 {{ fileCount }}
        </button>
      </div>

      <p v-if="err" class="error">{{ err }}</p>
      <p v-if="okMsg" class="ok">{{ okMsg }}</p>

      <div v-if="tab === 'overview'" class="panel">
        <h3>属性</h3>
        <p class="hint">这些字段与 SKILL.md 的 frontmatter 保持同步。</p>
        <label>
          名称
          <input v-model="editName" type="text" @input="markDirty" />
        </label>
        <label>
          描述
          <textarea v-model="editDescription" rows="5" @input="markDirty" />
          <small>{{ editDescription.length }} 个字符 · 智能体用这段文字判断是否加载该 skill</small>
        </label>
        <div class="actions">
          <button type="button" class="btn-add" :disabled="busy || !dirty" @click="saveOverview">
            {{ busy ? '保存中…' : '保存' }}
          </button>
        </div>

        <div class="usage">
          <div class="usage-head">
            <h3>被 {{ usedAgents.length }} 个智能体使用</h3>
            <button type="button" class="btn-ghost" :disabled="busy" @click="openBind">+ 添加到智能体</button>
          </div>
          <ul v-if="usedAgents.length" class="agent-list">
            <li v-for="a in usedAgents" :key="a.id">
              <button type="button" class="agent-row" @click="goAgent(a.id)">
                <AgentAvatar :src="a.avatar" :provider="a.provider" :size="28" />
                <span>{{ a.name }}</span>
              </button>
            </li>
          </ul>
          <div v-else class="empty-box">
            还未分配给任何智能体。打开某个智能体的 Skills 标签页进行分配，或点击「添加到智能体」。
          </div>
        </div>

        <p class="foot-note">你可以编辑和删除该技能。改动将在智能体下次运行时生效。</p>
      </div>

      <div v-else class="panel">
        <h3>SKILL.md</h3>
        <p class="hint">本轮以单文件正文管理；完整多文件目录树后续支持。</p>
        <textarea
          v-model="editContent"
          class="content"
          rows="18"
          spellcheck="false"
          @input="markDirty"
        />
        <div class="actions">
          <button type="button" class="btn-add" :disabled="busy || !dirty" @click="saveContent">
            {{ busy ? '保存中…' : '保存 SKILL.md' }}
          </button>
        </div>
      </div>
    </template>
    <div v-else class="muted pad">
      {{ err || 'Skill 不存在' }}
      <div class="actions" style="margin-top: 12px">
        <router-link class="btn-ghost" :to="{ name: 'skills' }">返回列表</router-link>
      </div>
    </div>

    <div v-if="showBind" class="modal-backdrop" @click.self="showBind = false">
      <div class="modal" role="dialog" aria-modal="true">
        <h3>添加到智能体</h3>
        <p class="hint">选择要挂载「{{ skill?.name }}」的智能体。</p>
        <ul class="bind-list">
          <li v-for="a in agents" :key="a.id">
            <label>
              <input
                type="checkbox"
                :checked="bindSelected.includes(a.id)"
                @change="toggleBind(a.id)"
              />
              <AgentAvatar :src="a.avatar" :provider="a.provider" :size="24" />
              <span>{{ a.name }}</span>
            </label>
          </li>
        </ul>
        <p v-if="!agents.length" class="muted">暂无可用智能体</p>
        <div class="modal-actions">
          <button type="button" class="btn-ghost" @click="showBind = false">取消</button>
          <button
            type="button"
            class="btn-add"
            :disabled="busy || !bindSelected.length"
            @click="confirmBind"
          >
            添加
          </button>
        </div>
      </div>
    </div>

    <ConfirmDialog
      v-if="pendingDelete && skill"
      :open="true"
      :title="`删除 skill「${skill.name}」？`"
      description="将从工作区移除该 skill，并解除智能体上的挂载。此操作不可恢复。"
      confirm-label="删除"
      tone="danger"
      :busy="busy"
      @cancel="pendingDelete = false"
      @confirm="confirmDelete"
    />
  </section>
</template>

<style scoped>
.page { max-width: 880px; }
.pad { padding: 24px 0; }
.muted { color: var(--muted); font-size: 13px; }
.error { color: var(--danger); font-size: 13px; }
.ok { color: #15803d; font-size: 13px; }
.top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.crumbs {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--muted);
}
.crumbs a { color: #2563eb; text-decoration: none; }
.sep { color: #d1d5db; }
.top-actions { display: flex; gap: 8px; align-items: center; }
.title-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 16px;
}
.skill-ico {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: #f3f4f6;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #4b5563;
  flex-shrink: 0;
}
.title-main h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
}
.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 14px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--muted);
}
.tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 16px;
}
.tabs button {
  border: none;
  background: transparent;
  padding: 10px 14px;
  font-size: 13px;
  font-weight: 600;
  color: var(--muted);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}
.tabs button.on {
  color: var(--text);
  border-bottom-color: var(--text);
}
.panel {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 18px;
}
.panel h3 { margin: 0 0 6px; font-size: 15px; }
.hint { margin: 0 0 14px; font-size: 12px; color: var(--muted); line-height: 1.45; }
label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 14px;
}
label small { font-weight: 400; color: var(--muted); }
input, textarea {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 8px 10px;
  font: inherit;
  font-size: 13px;
  font-weight: 400;
  color: var(--text);
  background: #fff;
}
textarea.content {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  line-height: 1.45;
  resize: vertical;
  width: 100%;
  box-sizing: border-box;
}
.actions { display: flex; gap: 8px; margin-bottom: 8px; }
.btn-add {
  border: none;
  background: #1c2333;
  color: #fff;
  border-radius: 8px;
  padding: 9px 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.btn-add:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-ghost {
  border: 1px solid var(--border);
  background: #fff;
  color: var(--text);
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  cursor: pointer;
  text-decoration: none;
  display: inline-flex;
}
.icon-btn {
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 8px;
  width: 36px;
  height: 36px;
  cursor: pointer;
}
.icon-btn.danger { color: var(--danger); }
.usage { margin-top: 28px; padding-top: 20px; border-top: 1px solid var(--border); }
.usage-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.usage-head h3 { margin: 0; }
.agent-list { list-style: none; margin: 0; padding: 0; }
.agent-list li { border-bottom: 1px solid var(--border); }
.agent-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  border: none;
  background: transparent;
  padding: 10px 4px;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  text-align: left;
}
.agent-row:hover { background: #f9fafb; }
.empty-box {
  border: 1px dashed var(--border);
  border-radius: 10px;
  padding: 20px;
  font-size: 13px;
  color: var(--muted);
  line-height: 1.5;
  text-align: center;
}
.foot-note {
  margin: 18px 0 0;
  padding: 10px 12px;
  background: #f9fafb;
  border-radius: 8px;
  font-size: 12px;
  color: var(--muted);
}
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 80;
  padding: 16px;
}
.modal {
  width: min(420px, 100%);
  background: #fff;
  border-radius: 14px;
  padding: 18px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.18);
}
.modal h3 { margin: 0 0 8px; }
.bind-list {
  list-style: none;
  margin: 0 0 12px;
  padding: 0;
  max-height: 280px;
  overflow: auto;
  border: 1px solid var(--border);
  border-radius: 10px;
}
.bind-list li { border-bottom: 1px solid var(--border); }
.bind-list li:last-child { border-bottom: none; }
.bind-list label {
  flex-direction: row;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  margin: 0;
  font-weight: 400;
  cursor: pointer;
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
