<script setup lang="ts">
/**
 * Skills：工作区共享指令；支持手动创建 / URL 导入 / 从运行时复制。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { apiFetch } from '@/lib/api'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import MoreMenu from '@/components/MoreMenu.vue'
import ActionIcon from '@/components/ActionIcon.vue'
import {
  defaultSkillMarkdown,
  formatSkillTime,
  sourceLabel,
  type RuntimeSkill,
  type Skill,
  type SkillUrlPreview,
} from '@/lib/skills'
import { displayName, providerLabel, type Runtime } from '@/lib/runtimes'

type Step = 'picker' | 'manual' | 'url' | 'runtime'

const items = ref<Skill[]>([])
const runtimes = ref<Runtime[]>([])
const loading = ref(false)
const busy = ref(false)
const err = ref('')
const showCreate = ref(false)
const step = ref<Step>('picker')
const menuOpenId = ref('')
const pendingDelete = ref<Skill | null>(null)

const manualName = ref('')
const manualContent = ref(defaultSkillMarkdown())

const urlInput = ref('')
const urlPreview = ref<SkillUrlPreview | null>(null)
const urlErr = ref('')

const selectedRuntimeId = ref('')
const runtimeSkills = ref<RuntimeSkill[]>([])
const runtimeSkillLoading = ref(false)
const selectedRuntimeSkillIds = ref<string[]>([])
const runtimeErr = ref('')

const onlineRuntimes = computed(() =>
  runtimes.value.filter((r) => (r.status || '').toLowerCase() === 'online'),
)

async function load() {
  loading.value = true
  err.value = ''
  try {
    const [skills, rts] = await Promise.all([
      apiFetch<Skill[]>('/api/skills'),
      apiFetch<Runtime[]>('/api/runtimes'),
    ])
    items.value = skills
    runtimes.value = rts
  } catch (e) {
    err.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  step.value = 'picker'
  err.value = ''
  urlErr.value = ''
  runtimeErr.value = ''
  manualName.value = ''
  manualContent.value = defaultSkillMarkdown()
  urlInput.value = ''
  urlPreview.value = null
  selectedRuntimeId.value = onlineRuntimes.value[0]?.id || ''
  runtimeSkills.value = []
  selectedRuntimeSkillIds.value = []
  showCreate.value = true
}

function closeCreate() {
  if (busy.value) return
  showCreate.value = false
}

function choose(s: Step) {
  step.value = s
  if (s === 'runtime' && selectedRuntimeId.value) {
    void loadRuntimeSkills()
  }
}

watch(selectedRuntimeId, () => {
  if (step.value === 'runtime') void loadRuntimeSkills()
})

async function createManual() {
  busy.value = true
  err.value = ''
  try {
    const name = manualName.value.trim()
    await apiFetch('/api/skills', {
      method: 'POST',
      body: JSON.stringify({
        name: name || undefined,
        content: manualContent.value,
        sourceType: 'manual',
      }),
    })
    showCreate.value = false
    await load()
  } catch (e) {
    err.value = e instanceof Error ? e.message : '创建失败'
  } finally {
    busy.value = false
  }
}

async function previewUrl() {
  urlErr.value = ''
  urlPreview.value = null
  const url = urlInput.value.trim()
  if (!url) {
    urlErr.value = '请输入 URL'
    return
  }
  busy.value = true
  try {
    urlPreview.value = await apiFetch<SkillUrlPreview>('/api/skills/preview-url', {
      method: 'POST',
      body: JSON.stringify({ url }),
    })
  } catch (e) {
    urlErr.value = e instanceof Error ? e.message : '预览失败'
  } finally {
    busy.value = false
  }
}

async function importUrl() {
  const url = urlInput.value.trim()
  if (!url) {
    urlErr.value = '请输入 URL'
    return
  }
  busy.value = true
  urlErr.value = ''
  try {
    await apiFetch('/api/skills/import-url', {
      method: 'POST',
      body: JSON.stringify({ url }),
    })
    showCreate.value = false
    await load()
  } catch (e) {
    urlErr.value = e instanceof Error ? e.message : '导入失败'
  } finally {
    busy.value = false
  }
}

async function loadRuntimeSkills() {
  runtimeSkills.value = []
  selectedRuntimeSkillIds.value = []
  runtimeErr.value = ''
  if (!selectedRuntimeId.value) return
  runtimeSkillLoading.value = true
  try {
    runtimeSkills.value = await apiFetch<RuntimeSkill[]>(
      `/api/runtimes/${selectedRuntimeId.value}/skills`,
    )
    if (!runtimeSkills.value.length) {
      runtimeErr.value = '该运行时尚未上报 skill。请确认本机 Daemon 在线，且已安装 SKILL.md。'
    }
  } catch (e) {
    runtimeErr.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    runtimeSkillLoading.value = false
  }
}

function toggleRuntimeSkill(id: string) {
  const set = new Set(selectedRuntimeSkillIds.value)
  if (set.has(id)) set.delete(id)
  else set.add(id)
  selectedRuntimeSkillIds.value = [...set]
}

async function promoteRuntimeSkills() {
  if (!selectedRuntimeId.value || !selectedRuntimeSkillIds.value.length) {
    runtimeErr.value = '请至少选择一个 skill'
    return
  }
  busy.value = true
  runtimeErr.value = ''
  try {
    for (const skillId of selectedRuntimeSkillIds.value) {
      await apiFetch('/api/skills/from-runtime', {
        method: 'POST',
        body: JSON.stringify({
          runtimeId: selectedRuntimeId.value,
          skillId,
        }),
      })
    }
    showCreate.value = false
    await load()
  } catch (e) {
    runtimeErr.value = e instanceof Error ? e.message : '复制失败'
  } finally {
    busy.value = false
  }
}

function setMenuOpen(id: string, open: boolean) {
  menuOpenId.value = open ? id : ''
}

function askDelete(s: Skill) {
  pendingDelete.value = s
}

async function confirmDelete() {
  if (!pendingDelete.value) return
  busy.value = true
  err.value = ''
  try {
    await apiFetch(`/api/skills/${pendingDelete.value.id}`, { method: 'DELETE' })
    pendingDelete.value = null
    await load()
  } catch (e) {
    err.value = e instanceof Error ? e.message : '删除失败'
  } finally {
    busy.value = false
  }
}

function runtimeOptionLabel(r: Runtime) {
  const host = r.hostName || ''
  const name = displayName(r) || providerLabel(r)
  return host ? `${name} · ${host}` : name
}

onMounted(load)
</script>

<template>
  <section class="page">
    <header class="head">
      <div>
        <h2>
          <span class="title-icon" aria-hidden="true">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
              <path
                d="M6 5.5A2.5 2.5 0 0 1 8.5 3H18v16H8.5A2.5 2.5 0 0 0 6 21.5V5.5Z"
                stroke="currentColor"
                stroke-width="1.6"
              />
              <path d="M6 18h12" stroke="currentColor" stroke-width="1.6" />
            </svg>
          </span>
          Skills
          <span v-if="items.length" class="count">{{ items.length }}</span>
        </h2>
        <p class="lead">
          工作区里任何智能体都能使用的指令。
          <a class="more" href="https://github.com/dawangsky/rudder" target="_blank" rel="noreferrer">了解更多 →</a>
        </p>
      </div>
      <button type="button" class="btn-add" @click="openCreate">+ 新建 skill</button>
    </header>

    <p v-if="err && !showCreate" class="error">{{ err }}</p>

    <div v-if="loading" class="empty-state">
      <p class="muted">加载中…</p>
    </div>

    <div v-else-if="!items.length" class="empty-state">
      <div class="empty-icon" aria-hidden="true">
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none">
          <path
            d="M6 5.5A2.5 2.5 0 0 1 8.5 3H18v16H8.5A2.5 2.5 0 0 0 6 21.5V5.5Z"
            stroke="currentColor"
            stroke-width="1.5"
          />
          <path d="M6 18h12" stroke="currentColor" stroke-width="1.5" />
        </svg>
      </div>
      <h3>还没有 skill</h3>
      <p>
        创建第一个 skill、从 URL 导入、或从已连接的运行时复制——之后工作区里每个智能体都能用它。
      </p>
      <button type="button" class="btn-add" @click="openCreate">+ 新建 skill</button>
    </div>

    <div v-else class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Skill</th>
            <th>来源</th>
            <th>更新</th>
            <th class="col-actions"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in items" :key="s.id">
            <td>
              <div class="skill-cell">
                <strong>{{ s.name }}</strong>
                <span class="desc">{{ s.description || '暂无描述' }}</span>
              </div>
            </td>
            <td>
              <span class="badge">{{ sourceLabel(s.sourceType) }}</span>
            </td>
            <td class="muted">{{ formatSkillTime(s.updatedAt || s.createdAt) }}</td>
            <td class="col-actions" @click.stop>
              <MoreMenu
                :open="menuOpenId === s.id"
                @update:open="(v) => setMenuOpen(s.id, v)"
              >
                <template #default="{ close }">
                  <button
                    type="button"
                    class="danger"
                    :disabled="busy"
                    @click="close(); askDelete(s)"
                  >
                    <ActionIcon name="delete" />
                    删除
                  </button>
                </template>
              </MoreMenu>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="showCreate" class="modal-backdrop" @click.self="closeCreate">
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="skill-create-title">
        <div class="modal-head">
          <div>
            <button
              v-if="step !== 'picker'"
              type="button"
              class="back"
              @click="step = 'picker'"
            >← 返回</button>
            <h3 id="skill-create-title">新建 skill</h3>
            <p v-if="step === 'picker'" class="modal-lead">选择一种方式把 skill 添加到工作区。</p>
            <p v-else-if="step === 'manual'" class="modal-lead">从空白 SKILL.md 开始，自己写指令。</p>
            <p v-else-if="step === 'url'" class="modal-lead">从 ClawHub 或 Skills.sh 拉取已发布的 skill。</p>
            <p v-else class="modal-lead">把本地运行时里已经装好的 skill 提升过来。</p>
          </div>
          <button type="button" class="modal-x" aria-label="关闭" @click="closeCreate">×</button>
        </div>

        <div v-if="step === 'picker'" class="method-list">
          <button type="button" class="method" @click="choose('manual')">
            <span class="method-icon">+</span>
            <span class="method-text">
              <strong>手动创建</strong>
              <small>从空白 SKILL.md 开始，自己写指令。</small>
            </span>
            <span class="chev">›</span>
          </button>
          <button type="button" class="method" @click="choose('url')">
            <span class="method-icon">↓</span>
            <span class="method-text">
              <strong>从 URL 导入</strong>
              <small>从 ClawHub 或 Skills.sh 拉取已发布的 skill。</small>
            </span>
            <span class="chev">›</span>
          </button>
          <button type="button" class="method" @click="choose('runtime')">
            <span class="method-icon">▣</span>
            <span class="method-text">
              <strong>从运行时复制</strong>
              <small>把本地运行时里已经装好的 skill 提升过来。</small>
            </span>
            <span class="chev">›</span>
          </button>
        </div>

        <div v-else-if="step === 'manual'" class="form">
          <label>
            名称
            <input v-model="manualName" type="text" placeholder="可留空，将使用 frontmatter 的 name" />
          </label>
          <label>
            SKILL.md
            <textarea v-model="manualContent" rows="14" spellcheck="false" />
          </label>
          <p v-if="err" class="error">{{ err }}</p>
          <div class="modal-actions">
            <button type="button" class="btn-ghost" :disabled="busy" @click="closeCreate">取消</button>
            <button type="button" class="btn-add" :disabled="busy" @click="createManual">
              {{ busy ? '创建中…' : '创建' }}
            </button>
          </div>
        </div>

        <div v-else-if="step === 'url'" class="form">
          <label>
            URL
            <input
              v-model="urlInput"
              type="url"
              placeholder="https://clawhub.ai/… 或 GitHub / raw SKILL.md"
            />
          </label>
          <div class="url-actions">
            <button type="button" class="btn-ghost" :disabled="busy" @click="previewUrl">预览</button>
            <button type="button" class="btn-add" :disabled="busy" @click="importUrl">
              {{ busy ? '导入中…' : '导入并创建' }}
            </button>
          </div>
          <p v-if="urlErr" class="error">{{ urlErr }}</p>
          <div v-if="urlPreview" class="preview">
            <strong>{{ urlPreview.name }}</strong>
            <p>{{ urlPreview.description || '暂无描述' }}</p>
            <pre>{{ urlPreview.content.slice(0, 1200) }}{{ urlPreview.content.length > 1200 ? '…' : '' }}</pre>
          </div>
        </div>

        <div v-else class="form">
          <label>
            运行时
            <select v-model="selectedRuntimeId">
              <option disabled value="">选择在线运行时</option>
              <option v-for="r in onlineRuntimes" :key="r.id" :value="r.id">
                {{ runtimeOptionLabel(r) }}
              </option>
            </select>
          </label>
          <p v-if="!onlineRuntimes.length" class="hint">
            当前没有在线运行时。请先启动本机 Daemon，并确保已连接运行时。
          </p>
          <p v-if="runtimeSkillLoading" class="muted">正在加载…</p>
          <ul v-else-if="runtimeSkills.length" class="rt-skills">
            <li v-for="rs in runtimeSkills" :key="rs.id">
              <label>
                <input
                  type="checkbox"
                  :checked="selectedRuntimeSkillIds.includes(rs.id)"
                  @change="toggleRuntimeSkill(rs.id)"
                />
                <span>
                  <strong>{{ rs.name }}</strong>
                  <small>{{ rs.description || rs.sourcePath }}</small>
                </span>
              </label>
            </li>
          </ul>
          <p v-if="runtimeErr" class="error">{{ runtimeErr }}</p>
          <div class="modal-actions">
            <button type="button" class="btn-ghost" :disabled="busy" @click="closeCreate">取消</button>
            <button
              type="button"
              class="btn-add"
              :disabled="busy || !selectedRuntimeSkillIds.length"
              @click="promoteRuntimeSkills"
            >
              {{ busy ? '复制中…' : '复制到工作区' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <ConfirmDialog
      v-if="pendingDelete"
      :open="true"
      :title="`删除 skill「${pendingDelete.name}」？`"
      description="将从工作区移除该 skill，并解除智能体上的挂载。此操作不可恢复。"
      confirm-label="删除"
      tone="danger"
      :busy="busy"
      @cancel="pendingDelete = null"
      @confirm="confirmDelete"
    />
  </section>
</template>

<style scoped>
.page {
  max-width: 960px;
  min-height: calc(100vh - 96px);
  display: flex;
  flex-direction: column;
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 18px;
  flex-shrink: 0;
}
h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
}
.title-icon {
  display: inline-flex;
  color: var(--text);
}
.count { color: var(--muted); font-weight: 600; margin-left: 2px; }
.lead { margin: 8px 0 0; font-size: 13px; color: var(--muted); line-height: 1.5; }
.more { color: #2563eb; text-decoration: none; }
.more:hover { text-decoration: underline; }
.btn-add {
  border: none;
  background: #1c2333;
  color: #fff;
  border-radius: 8px;
  padding: 9px 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
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
}
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  gap: 10px;
  /* 相对视口垂直居中再略偏下 */
  padding: 8vh 20px 22vh;
  margin-top: 0;
}
.empty-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #f3f4f6;
  color: #6b7280;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 4px;
}
.empty-state h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 650;
}
.empty-state p {
  margin: 0 0 8px;
  max-width: 420px;
  font-size: 13px;
  color: var(--muted);
  line-height: 1.55;
}
.table-wrap {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: auto;
}
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td {
  padding: 12px 14px;
  text-align: left;
  border-bottom: 1px solid var(--border);
  vertical-align: middle;
}
th {
  font-size: 12px;
  color: var(--muted);
  font-weight: 600;
}
tr:last-child td { border-bottom: none; }
.skill-cell { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.skill-cell strong { font-weight: 650; }
.desc {
  color: var(--muted);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 420px;
}
.badge {
  display: inline-flex;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
  background: #f3f4f6;
  color: #4b5563;
}
.col-actions { width: 48px; text-align: right; }
.muted { color: var(--muted); }
.error { color: var(--danger); margin: 0 0 10px; font-size: 13px; }
.hint { font-size: 13px; color: var(--muted); margin: 0 0 8px; line-height: 1.45; }

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
  width: min(520px, 100%);
  background: #fff;
  border-radius: 14px;
  padding: 18px 18px 16px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.18);
  max-height: min(90vh, 720px);
  overflow: auto;
}
.modal-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 14px;
}
.modal-head h3 { margin: 0; font-size: 18px; }
.modal-lead { margin: 6px 0 0; font-size: 13px; color: var(--muted); }
.back {
  border: none;
  background: transparent;
  color: #2563eb;
  padding: 0;
  margin-bottom: 6px;
  font-size: 13px;
  cursor: pointer;
}
.modal-x {
  border: none;
  background: transparent;
  font-size: 22px;
  line-height: 1;
  color: var(--muted);
  cursor: pointer;
  padding: 0 4px;
}
.modal-x:hover { color: var(--text); }

.method-list { display: flex; flex-direction: column; gap: 8px; }
.method {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  text-align: left;
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 12px;
  padding: 14px 12px;
  cursor: pointer;
}
.method:hover { background: #f9fafb; border-color: #d1d5db; }
.method-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: #f3f4f6;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 700;
  color: #374151;
  flex-shrink: 0;
}
.method-text { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }
.method-text strong { font-size: 14px; }
.method-text small { color: var(--muted); font-size: 12px; line-height: 1.4; }
.chev { color: #9ca3af; font-size: 20px; }

.form label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 12px;
}
.form input,
.form textarea,
.form select {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 8px 10px;
  font: inherit;
  font-size: 13px;
  font-weight: 400;
  color: var(--text);
  background: #fff;
}
.form textarea {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  line-height: 1.45;
  resize: vertical;
}
.modal-actions,
.url-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 4px;
}
.url-actions { margin-bottom: 10px; }
.preview {
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 12px;
  background: #f9fafb;
}
.preview strong { font-size: 14px; }
.preview p { margin: 4px 0 8px; font-size: 12px; color: var(--muted); }
.preview pre {
  margin: 0;
  white-space: pre-wrap;
  font-size: 11px;
  color: #374151;
  max-height: 220px;
  overflow: auto;
}
.rt-skills {
  list-style: none;
  margin: 0 0 12px;
  padding: 0;
  border: 1px solid var(--border);
  border-radius: 10px;
  max-height: 260px;
  overflow: auto;
}
.rt-skills li { border-bottom: 1px solid var(--border); }
.rt-skills li:last-child { border-bottom: none; }
.rt-skills label {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  margin: 0;
  font-weight: 400;
  cursor: pointer;
}
.rt-skills strong { display: block; font-size: 13px; }
.rt-skills small { display: block; color: var(--muted); font-size: 12px; margin-top: 2px; }
</style>
