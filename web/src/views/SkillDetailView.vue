<script setup lang="ts">
/**
 * Skill 详情：概览编辑、文件双栏（主文件 / 附属）+ 预览/编辑。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiFetch } from '@/lib/api'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import ActionIcon from '@/components/ActionIcon.vue'
import AgentAvatar from '@/components/AgentAvatar.vue'
import { renderMarkdown } from '@/lib/markdown'
import {
  formatSkillDisplayPath,
  formatSkillTime,
  skillOriginFromPath,
  sourceLabel,
  type Skill,
} from '@/lib/skills'
import type { Agent } from '@/lib/agents'

type Tab = 'overview' | 'files'
type FileMode = 'preview' | 'edit'

const route = useRoute()
const router = useRouter()

const skill = ref<Skill | null>(null)
const agents = ref<Agent[]>([])
const loading = ref(false)
const busy = ref(false)
const err = ref('')
const okMsg = ref('')
const tab = ref<Tab>('overview')
const fileMode = ref<FileMode>('preview')
const selectedFile = ref('SKILL.md')

const editName = ref('')
const editDescription = ref('')
const editContent = ref('')
const overviewDirty = ref(false)
const contentDirty = ref(false)

const pendingDelete = ref(false)
const showBind = ref(false)
const bindSelected = ref<string[]>([])

const skillId = computed(() => String(route.params.skillId || ''))

const usedAgents = computed(() => skill.value?.agents || [])

const fileCount = computed(() => 1)
const companionCount = computed(() => 0)

const originLabel = computed(() => {
  const s = skill.value
  if (!s) return ''
  const origin = skillOriginFromPath(s.sourceRef)
  switch (origin) {
    case 'codex':
      return 'Codex'
    case 'claude':
      return 'Claude'
    case 'cursor':
      return 'Cursor'
    case 'agents':
      return 'Agents'
    case 'openclaw':
      return 'OpenClaw'
    default:
      return sourceLabel(s.sourceType)
  }
})

const sourceHint = computed(() => {
  const s = skill.value
  if (!s) return ''
  if (s.sourceType === 'runtime') {
    return `本地运行时 · ${originLabel.value}`
  }
  if (s.sourceType === 'url' && s.sourceRef) return `URL · ${s.sourceRef}`
  return sourceLabel(s.sourceType)
})

const previewHtml = computed(() => renderMarkdown(editContent.value || skill.value?.content || ''))

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
    overviewDirty.value = false
    contentDirty.value = false
    fileMode.value = 'preview'
    selectedFile.value = 'SKILL.md'
  } catch (e) {
    err.value = e instanceof Error ? e.message : '加载失败'
    skill.value = null
  } finally {
    loading.value = false
  }
}

function markOverviewDirty() {
  overviewDirty.value = true
  okMsg.value = ''
}

function markContentDirty() {
  contentDirty.value = true
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
    overviewDirty.value = false
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
    contentDirty.value = false
    okMsg.value = '已保存 SKILL.md'
    fileMode.value = 'preview'
  } catch (e) {
    err.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    busy.value = false
  }
}

function setFileMode(mode: FileMode) {
  fileMode.value = mode
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
      <nav class="crumbs">
        <router-link :to="{ name: 'skills' }">Skills</router-link>
        <span class="sep">›</span>
        <span>{{ skill.name }}</span>
      </nav>

      <header class="hero">
        <div class="hero-left">
          <span class="skill-ico" aria-hidden="true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
              <path
                d="M8 4h8a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z"
                stroke="currentColor"
                stroke-width="1.5"
              />
              <path d="M9 9h6M9 13h6M9 17h4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
            </svg>
          </span>
          <div class="hero-text">
            <h1>{{ skill.name }}</h1>
            <div class="meta">
              <span class="meta-item" :title="formatSkillDisplayPath(skill.sourceRef) || sourceHint">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <rect x="3" y="5" width="18" height="14" rx="2" stroke="currentColor" stroke-width="1.5" />
                  <path d="M7 15h4M13 15h4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
                </svg>
                {{ sourceHint }}
              </span>
              <span class="meta-item">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path
                    d="M7 4h7l3 3v13a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z"
                    stroke="currentColor"
                    stroke-width="1.5"
                  />
                  <path d="M14 4v4h4" stroke="currentColor" stroke-width="1.5" />
                </svg>
                {{ fileCount }} 个文件
              </span>
              <span class="meta-item">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <circle cx="9" cy="9" r="3" stroke="currentColor" stroke-width="1.5" />
                  <circle cx="16" cy="10" r="2.5" stroke="currentColor" stroke-width="1.5" />
                  <path
                    d="M3.5 19c1.2-2.6 3.4-4 5.5-4s4.3 1.4 5.5 4M14 15c1.5 0 3 .8 4 2.5"
                    stroke="currentColor"
                    stroke-width="1.5"
                    stroke-linecap="round"
                  />
                </svg>
                {{ skill.agentCount || 0 }} 个智能体在用
              </span>
              <span class="meta-item">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <circle cx="12" cy="12" r="8" stroke="currentColor" stroke-width="1.5" />
                  <path d="M12 8v4.5L15 15" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
                </svg>
                {{ skill.createdBy || '未知' }} 于 {{ formatSkillTime(skill.updatedAt || skill.createdAt) }}更新
              </span>
            </div>
          </div>
        </div>
        <div class="hero-actions">
          <button type="button" class="btn-add" :disabled="busy" @click="openBind">
            <ActionIcon name="assign" />
            添加到智能体
          </button>
          <button
            type="button"
            class="icon-btn danger"
            title="删除"
            aria-label="删除"
            :disabled="busy"
            @click="pendingDelete = true"
          >
            <ActionIcon name="delete" />
          </button>
        </div>
      </header>

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
          <input v-model="editName" type="text" @input="markOverviewDirty" />
        </label>
        <label>
          描述
          <textarea v-model="editDescription" rows="5" @input="markOverviewDirty" />
          <small>{{ editDescription.length }} 个字符 · 智能体用这段文字判断是否加载该 skill</small>
        </label>
        <div class="actions">
          <button type="button" class="btn-add" :disabled="busy || !overviewDirty" @click="saveOverview">
            {{ busy ? '保存中…' : '保存' }}
          </button>
        </div>

        <div class="usage">
          <div class="usage-head">
            <h3>被 {{ usedAgents.length }} 个智能体使用</h3>
            <button type="button" class="btn-ghost" :disabled="busy" @click="openBind">
              <ActionIcon name="assign" />
              添加到智能体
            </button>
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

      <div v-else class="files-panel">
        <aside class="file-nav">
          <div class="file-group">
            <div class="file-group-title">主文件</div>
            <button
              type="button"
              class="file-item"
              :class="{ on: selectedFile === 'SKILL.md' }"
              @click="selectedFile = 'SKILL.md'"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path
                  d="M7 4h7l3 3v13a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z"
                  stroke="currentColor"
                  stroke-width="1.5"
                />
              </svg>
              SKILL.md
            </button>
          </div>
          <div class="file-group">
            <div class="file-group-title">附属文件 {{ companionCount }}</div>
            <p class="file-empty">暂无附属文件。</p>
          </div>
          <button
            type="button"
            class="btn-new-file"
            disabled
            title="完整多文件目录树后续支持"
          >
            + 新建文件
          </button>
        </aside>

        <div class="file-main">
          <div class="file-toolbar">
            <strong>{{ selectedFile }}</strong>
            <div class="mode-toggle" role="tablist" aria-label="文件模式">
              <button
                type="button"
                role="tab"
                :aria-selected="fileMode === 'preview'"
                :class="{ on: fileMode === 'preview' }"
                @click="setFileMode('preview')"
              >
                预览
              </button>
              <button
                type="button"
                role="tab"
                :aria-selected="fileMode === 'edit'"
                :class="{ on: fileMode === 'edit' }"
                @click="setFileMode('edit')"
              >
                编辑
              </button>
            </div>
          </div>

          <div v-if="fileMode === 'preview'" class="md-preview" v-html="previewHtml" />
          <div v-else class="edit-pane">
            <textarea
              v-model="editContent"
              class="content"
              spellcheck="false"
              @input="markContentDirty"
            />
            <div class="edit-actions">
              <button
                type="button"
                class="btn-add"
                :disabled="busy || !contentDirty"
                @click="saveContent"
              >
                {{ busy ? '保存中…' : '保存 SKILL.md' }}
              </button>
            </div>
          </div>
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
.page { max-width: 1080px; }
.pad { padding: 24px 0; }
.muted { color: var(--muted); font-size: 13px; }
.error { color: var(--danger); font-size: 13px; margin: 0 0 12px; }
.ok { color: #15803d; font-size: 13px; margin: 0 0 12px; }

.crumbs {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--muted);
  margin-bottom: 16px;
}
.crumbs a { color: #2563eb; text-decoration: none; }
.sep { color: #d1d5db; }

.hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 18px;
}
.hero-left {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  min-width: 0;
}
.skill-ico {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: #f3f4f6;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #4b5563;
  flex-shrink: 0;
}
.hero-text { min-width: 0; }
.hero-text h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
  word-break: break-word;
}
.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--muted);
}
.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  max-width: 100%;
}
.meta-item svg { flex-shrink: 0; }
.hero-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;
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
  border-bottom-color: #2563eb;
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

.files-panel {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 0;
  min-height: 480px;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
}
.file-nav {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 14px 12px;
  border-right: 1px solid var(--border);
  background: #fafafa;
}
.file-group-title {
  font-size: 11px;
  font-weight: 700;
  color: var(--muted);
  letter-spacing: 0.02em;
  margin-bottom: 6px;
  padding: 0 6px;
}
.file-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  border: none;
  background: transparent;
  border-radius: 8px;
  padding: 8px 10px;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
  cursor: pointer;
  text-align: left;
}
.file-item:hover { background: #f3f4f6; }
.file-item.on { background: #eef2f7; }
.file-empty {
  margin: 0;
  padding: 4px 10px;
  font-size: 12px;
  color: var(--muted);
}
.btn-new-file {
  margin-top: auto;
  border: 1px dashed var(--border);
  background: #fff;
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
  color: var(--muted);
  cursor: not-allowed;
}
.file-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 480px;
}
.file-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
}
.file-toolbar strong {
  font-size: 13px;
  font-weight: 700;
}
.mode-toggle {
  display: inline-flex;
  border: 1px solid var(--border);
  border-radius: 8px;
  overflow: hidden;
  background: #f9fafb;
}
.mode-toggle button {
  border: none;
  background: transparent;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  color: var(--muted);
  cursor: pointer;
}
.mode-toggle button.on {
  background: #fff;
  color: var(--text);
  box-shadow: 0 0 0 1px var(--border);
}
.md-preview {
  flex: 1;
  padding: 20px 24px 28px;
  overflow: auto;
  font-size: 14px;
  line-height: 1.65;
  color: var(--text);
}
.md-preview :deep(h1),
.md-preview :deep(h2),
.md-preview :deep(h3) {
  margin: 1.2em 0 0.5em;
  line-height: 1.3;
  font-weight: 700;
}
.md-preview :deep(h1) { margin-top: 0; font-size: 1.6em; }
.md-preview :deep(h2) { font-size: 1.25em; }
.md-preview :deep(h3) { font-size: 1.1em; }
.md-preview :deep(p) { margin: 0.7em 0; }
.md-preview :deep(ul),
.md-preview :deep(ol) {
  margin: 0.7em 0;
  padding-left: 1.4em;
}
.md-preview :deep(li) { margin: 0.25em 0; }
.md-preview :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.9em;
  background: #f3f4f6;
  border-radius: 4px;
  padding: 0.1em 0.35em;
}
.md-preview :deep(pre) {
  margin: 0.9em 0;
  padding: 12px 14px;
  background: #f3f4f6;
  border-radius: 8px;
  overflow: auto;
}
.md-preview :deep(pre code) {
  background: transparent;
  padding: 0;
  font-size: 12.5px;
  line-height: 1.5;
}
.md-preview :deep(a) { color: #2563eb; }
.md-preview :deep(blockquote) {
  margin: 0.8em 0;
  padding: 0.2em 0 0.2em 12px;
  border-left: 3px solid #d1d5db;
  color: var(--muted);
}
.md-preview :deep(hr) {
  border: none;
  border-top: 1px solid var(--border);
  margin: 1.2em 0;
}
.edit-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
textarea.content {
  flex: 1;
  width: 100%;
  min-height: 360px;
  box-sizing: border-box;
  border: none;
  border-radius: 0;
  resize: none;
  padding: 16px 18px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  line-height: 1.5;
  outline: none;
}
.edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--border);
  background: #fafafa;
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
  display: inline-flex;
  align-items: center;
  gap: 6px;
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
  align-items: center;
  gap: 6px;
}
.icon-btn {
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 8px;
  width: 36px;
  height: 36px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--text);
}
.icon-btn.danger { color: var(--danger); }
.icon-btn:disabled { opacity: 0.5; cursor: not-allowed; }

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

@media (max-width: 820px) {
  .hero { flex-direction: column; }
  .files-panel {
    grid-template-columns: 1fr;
    min-height: 0;
  }
  .file-nav {
    border-right: none;
    border-bottom: 1px solid var(--border);
  }
  .btn-new-file { margin-top: 0; }
}
</style>
