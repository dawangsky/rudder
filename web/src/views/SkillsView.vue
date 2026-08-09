<script setup lang="ts">
/**
 * Skills：工作区共享指令；支持手动创建 / URL 导入 / 从运行时复制。
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { apiFetch } from '@/lib/api'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import MoreMenu from '@/components/MoreMenu.vue'
import ActionIcon from '@/components/ActionIcon.vue'
import ProviderIcon from '@/components/ProviderIcon.vue'
import { getHostBridge, isDesktopHost } from '@/lib/hostBridge'
import { getCustomProviderIcon, ICONS_CHANGED_EVENT } from '@/lib/providerIcons'
import {
  defaultSkillMarkdown,
  formatSkillDisplayPath,
  formatSkillTime,
  skillOriginFromPath,
  sourceLabel,
  type RuntimeSkill,
  type Skill,
  type SkillUrlPreview,
} from '@/lib/skills'
import { displayName, iconProvider, providerLabel, type Runtime } from '@/lib/runtimes'

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
/** 是否已完成本机扫描（Desktop 下切换运行时无需重扫） */
const localSkillsReady = ref(false)
const selectedRuntimeSkillIds = ref<string[]>([])
const runtimeErr = ref('')
const runtimeQuery = ref('')
/** 仅选中 1 个时，可编辑导入到工作区的名称/描述 */
const importName = ref('')
const importDescription = ref('')
/** 自定义运行时下拉（原生 select 无法在选项中画图标） */
const runtimeMenuOpen = ref(false)
/** 自定义图标变更时递增，驱动重绘 */
const iconTick = ref(0)

function onIconsChanged() {
  iconTick.value += 1
}

function customIconFor(r: Runtime | null | undefined) {
  void iconTick.value
  if (!r) return ''
  return getCustomProviderIcon(r.daemonId, r.provider)
}

const onlineRuntimes = computed(() =>
  runtimes.value.filter((r) => (r.status || '').toLowerCase() === 'online'),
)

const selectedRuntime = computed(() =>
  onlineRuntimes.value.find((r) => r.id === selectedRuntimeId.value) || null,
)

const filteredRuntimeSkills = computed(() => {
  const q = runtimeQuery.value.trim().toLowerCase()
  if (!q) return runtimeSkills.value
  return runtimeSkills.value.filter((s) => {
    const hay = `${s.name} ${s.description || ''} ${s.displayPath || s.sourcePath} ${s.origin || ''}`.toLowerCase()
    return hay.includes(q)
  })
})

const allFilteredSelected = computed(() => {
  const list = filteredRuntimeSkills.value
  return list.length > 0 && list.every((s) => selectedRuntimeSkillIds.value.includes(s.id))
})

const selectedCount = computed(() => selectedRuntimeSkillIds.value.length)

const soleSelectedSkill = computed(() => {
  if (selectedRuntimeSkillIds.value.length !== 1) return null
  const id = selectedRuntimeSkillIds.value[0]
  return runtimeSkills.value.find((s) => s.id === id) || null
})

watch(soleSelectedSkill, (sk) => {
  if (sk) {
    importName.value = sk.name
    importDescription.value = sk.description || ''
  } else {
    importName.value = ''
    importDescription.value = ''
  }
})

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
  localSkillsReady.value = false
  selectedRuntimeSkillIds.value = []
  runtimeQuery.value = ''
  importName.value = ''
  importDescription.value = ''
  runtimeMenuOpen.value = false
  showCreate.value = true
}

function closeCreate() {
  if (busy.value) return
  runtimeMenuOpen.value = false
  showCreate.value = false
}

function choose(s: Step) {
  step.value = s
  runtimeMenuOpen.value = false
  if (s === 'runtime') {
    if (!selectedRuntimeId.value && onlineRuntimes.value[0]) {
      selectedRuntimeId.value = onlineRuntimes.value[0].id
    }
    void loadRuntimeSkills({ soft: false })
  }
}

function selectRuntime(id: string) {
  if (selectedRuntimeId.value === id) {
    runtimeMenuOpen.value = false
    return
  }
  selectedRuntimeId.value = id
  runtimeMenuOpen.value = false
  // Desktop 本机扫描与所选运行时无关：只换图标/状态卡，不重扫、不闪列表
  if (isDesktopHost() && localSkillsReady.value) return
  void loadRuntimeSkills({ soft: true })
}

function onDocPointerDown(ev: PointerEvent) {
  if (!runtimeMenuOpen.value) return
  const t = ev.target
  if (!(t instanceof Element)) return
  if (t.closest('.rt-combo')) return
  runtimeMenuOpen.value = false
}

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

function normalizeReported(list: RuntimeSkill[]): RuntimeSkill[] {
  return list.map((s) => ({
    ...s,
    source: 'reported' as const,
    origin: s.origin || skillOriginFromPath(s.sourcePath),
    displayPath: s.displayPath || formatSkillDisplayPath(s.sourcePath),
    fileCount: s.fileCount ?? 1,
  }))
}

async function loadRuntimeSkills(opts?: { soft?: boolean }) {
  const soft = !!opts?.soft
  runtimeErr.value = ''
  // soft：保留现有列表，避免切换运行时整页闪白；仅首扫时显示 loading
  if (!soft || !runtimeSkills.value.length) {
    runtimeSkillLoading.value = true
  }
  if (!soft) {
    selectedRuntimeSkillIds.value = []
  }
  try {
    // Desktop：直接扫本机磁盘（与目标 UI 一致，不依赖 Daemon 上报缓存）
    if (isDesktopHost()) {
      if (localSkillsReady.value && runtimeSkills.value.length) {
        return
      }
      const res = await getHostBridge().scanLocalSkills()
      if (res.ok && res.skills.length) {
        runtimeSkills.value = res.skills.map((s) => ({
          id: s.id,
          name: s.name,
          description: s.description,
          sourcePath: s.sourcePath,
          displayPath: s.displayPath,
          contentHash: s.contentHash,
          origin: s.origin,
          fileCount: s.fileCount,
          content: s.content,
          source: 'local',
        }))
        localSkillsReady.value = true
        return
      }
      localSkillsReady.value = res.ok
      if (res.message && !res.ok) {
        runtimeErr.value = res.message
      }
    }

    if (!selectedRuntimeId.value) {
      if (!runtimeSkills.value.length) {
        runtimeErr.value = onlineRuntimes.value.length
          ? '请选择运行时'
          : '当前没有在线运行时。请先启动本机 Daemon。'
      }
      return
    }

    const reported = await apiFetch<RuntimeSkill[]>(
      `/api/runtimes/${selectedRuntimeId.value}/skills`,
    )
    if (reported.length) {
      runtimeSkills.value = normalizeReported(reported)
      if (!soft) selectedRuntimeSkillIds.value = []
      return
    }

    if (!soft) runtimeSkills.value = []
    if (!runtimeSkills.value.length) {
      runtimeErr.value = isDesktopHost()
        ? '本机未发现 SKILL.md。常见目录：~/.agents/skills、~/.claude/skills、~/.cursor/skills。'
        : '该运行时尚未上报 skill。请确认本机 Daemon 在线，且已安装 SKILL.md。'
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

function toggleSelectAllFiltered() {
  const ids = filteredRuntimeSkills.value.map((s) => s.id)
  if (!ids.length) return
  if (allFilteredSelected.value) {
    const drop = new Set(ids)
    selectedRuntimeSkillIds.value = selectedRuntimeSkillIds.value.filter((id) => !drop.has(id))
  } else {
    const set = new Set(selectedRuntimeSkillIds.value)
    for (const id of ids) set.add(id)
    selectedRuntimeSkillIds.value = [...set]
  }
}

async function promoteRuntimeSkills() {
  if (!selectedRuntimeSkillIds.value.length) {
    runtimeErr.value = '请至少选择一个 skill'
    return
  }
  const sole = soleSelectedSkill.value
  if (sole) {
    const name = importName.value.trim()
    if (!name) {
      runtimeErr.value = '请填写工作区里的 skill 名称'
      return
    }
  }
  busy.value = true
  runtimeErr.value = ''
  try {
    const byId = new Map(runtimeSkills.value.map((s) => [s.id, s]))
    const soleId = sole?.id
    for (const skillId of selectedRuntimeSkillIds.value) {
      const sk = byId.get(skillId)
      if (!sk) continue
      const name =
        soleId && skillId === soleId ? importName.value.trim() || sk.name : sk.name
      const description =
        soleId && skillId === soleId
          ? importDescription.value.trim() || undefined
          : sk.description || undefined
      if (sk.source === 'local' && sk.content) {
        await apiFetch('/api/skills', {
          method: 'POST',
          body: JSON.stringify({
            name,
            description,
            content: sk.content,
            sourceType: 'runtime',
            sourceRef: sk.sourcePath,
          }),
        })
      } else if (selectedRuntimeId.value) {
        // Daemon 上报路径无正文时走 from-runtime；单选改名则改为带内容创建不可用时仍用原接口
        if (soleId && skillId === soleId && sk.content) {
          await apiFetch('/api/skills', {
            method: 'POST',
            body: JSON.stringify({
              name,
              description,
              content: sk.content,
              sourceType: 'runtime',
              sourceRef: sk.sourcePath,
            }),
          })
        } else {
          await apiFetch('/api/skills/from-runtime', {
            method: 'POST',
            body: JSON.stringify({
              runtimeId: selectedRuntimeId.value,
              skillId,
            }),
          })
        }
      }
    }
    showCreate.value = false
    await load()
  } catch (e) {
    runtimeErr.value = e instanceof Error ? e.message : '导入失败'
  } finally {
    busy.value = false
  }
}

function runtimeFooterHint() {
  if (!selectedCount.value) return '请选择一个 skill 继续。'
  if (soleSelectedSkill.value) {
    const name = importName.value.trim() || soleSelectedSkill.value.name
    return `准备导入 ${name} 到工作区。`
  }
  return `已选择 ${selectedCount.value} 个 skill`
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
  return host ? `${name} (${host})` : name
}

function skillTag(s: RuntimeSkill) {
  // 与目标 UI 一致：优先展示所选运行时短名；否则回退到来源目录标签
  if (selectedRuntime.value) return displayName(selectedRuntime.value).toLowerCase()
  return s.origin || skillOriginFromPath(s.sourcePath)
}

function skillPath(s: RuntimeSkill) {
  return s.displayPath || formatSkillDisplayPath(s.sourcePath)
}

onMounted(() => {
  window.addEventListener(ICONS_CHANGED_EVENT, onIconsChanged)
  document.addEventListener('pointerdown', onDocPointerDown, true)
  void load()
})
onUnmounted(() => {
  window.removeEventListener(ICONS_CHANGED_EVENT, onIconsChanged)
  document.removeEventListener('pointerdown', onDocPointerDown, true)
})
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
      <div
        class="modal"
        :class="{ 'modal-wide': step === 'runtime' }"
        role="dialog"
        aria-modal="true"
        aria-labelledby="skill-create-title"
      >
        <div class="modal-head">
          <div class="modal-head-main">
            <button
              v-if="step !== 'picker'"
              type="button"
              class="back-icon"
              aria-label="返回"
              @click="step = 'picker'"
            >
              ←
            </button>
            <div>
              <h3 id="skill-create-title">
                {{ step === 'runtime' ? '从运行时复制' : '新建 skill' }}
              </h3>
              <p v-if="step === 'picker'" class="modal-lead">选择一种方式把 skill 添加到工作区。</p>
              <p v-else-if="step === 'manual'" class="modal-lead">从空白 SKILL.md 开始，自己写指令。</p>
              <p v-else-if="step === 'url'" class="modal-lead">从 ClawHub 或 Skills.sh 拉取已发布的 skill。</p>
              <p v-else class="modal-lead">扫描本地运行时，把它磁盘上的 skill 提升到工作区。</p>
            </div>
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

        <div v-else class="runtime-panel">
          <div class="rt-label">
            运行时
            <div class="rt-combo" :class="{ open: runtimeMenuOpen }">
              <button
                type="button"
                class="rt-combo-trigger"
                :disabled="!onlineRuntimes.length"
                aria-haspopup="listbox"
                :aria-expanded="runtimeMenuOpen"
                @click="runtimeMenuOpen = !runtimeMenuOpen"
              >
                <ProviderIcon
                  v-if="selectedRuntime"
                  :provider="iconProvider(selectedRuntime)"
                  :custom-src="customIconFor(selectedRuntime)"
                  :size="18"
                  :title="providerLabel(selectedRuntime)"
                />
                <span class="rt-combo-label">
                  {{
                    selectedRuntime
                      ? runtimeOptionLabel(selectedRuntime)
                      : '选择在线运行时'
                  }}
                </span>
                <span class="rt-combo-chev" aria-hidden="true">▾</span>
              </button>
              <ul
                v-if="runtimeMenuOpen"
                class="rt-combo-menu"
                role="listbox"
              >
                <li v-for="r in onlineRuntimes" :key="r.id" role="option">
                  <button
                    type="button"
                    class="rt-combo-option"
                    :class="{ active: r.id === selectedRuntimeId }"
                    @click="selectRuntime(r.id)"
                  >
                    <ProviderIcon
                      :provider="iconProvider(r)"
                      :custom-src="customIconFor(r)"
                      :size="18"
                      :title="providerLabel(r)"
                    />
                    <span>{{ runtimeOptionLabel(r) }}</span>
                  </button>
                </li>
              </ul>
            </div>
          </div>

          <div v-if="selectedRuntime" class="rt-card">
            <ProviderIcon
              class="rt-card-icon"
              :provider="iconProvider(selectedRuntime)"
              :custom-src="customIconFor(selectedRuntime)"
              :size="22"
              :title="providerLabel(selectedRuntime)"
            />
            <div class="rt-card-text">
              <strong>{{ displayName(selectedRuntime) }}</strong>
              <small>{{ selectedRuntime.hostName || '本机' }}</small>
            </div>
            <span
              class="rt-status"
              :class="{ online: (selectedRuntime.status || '').toLowerCase() === 'online' }"
            >
              {{ (selectedRuntime.status || '').toLowerCase() === 'online' ? 'online' : (selectedRuntime.status || 'offline') }}
            </span>
          </div>

          <p v-if="!onlineRuntimes.length" class="hint">
            当前没有在线运行时。请先启动本机 Daemon，并确保已连接运行时。
          </p>

          <div
            v-if="runtimeSkillLoading && !runtimeSkills.length"
            class="rt-loading muted"
          >
            正在扫描本机 skill…
          </div>

          <template v-else-if="runtimeSkills.length">
            <div class="rt-search">
              <span class="rt-search-icon" aria-hidden="true">⌕</span>
              <input v-model="runtimeQuery" type="search" placeholder="搜索本地 skill" />
            </div>

            <label class="rt-select-all">
              <input
                type="checkbox"
                :checked="allFilteredSelected"
                @change="toggleSelectAllFiltered"
              />
              全选 ({{ filteredRuntimeSkills.length }})
            </label>

            <ul class="rt-skills">
              <li v-for="rs in filteredRuntimeSkills" :key="rs.id">
                <label class="rt-skill">
                  <input
                    type="checkbox"
                    :checked="selectedRuntimeSkillIds.includes(rs.id)"
                    @change="toggleRuntimeSkill(rs.id)"
                  />
                  <span class="rt-skill-body">
                    <span class="rt-skill-top">
                      <span class="rt-skill-title">
                        <strong>{{ rs.name }}</strong>
                        <span class="rt-tag">{{ skillTag(rs) }}</span>
                      </span>
                      <span class="rt-files">{{ rs.fileCount ?? 1 }}个文件</span>
                    </span>
                    <span class="rt-skill-desc">{{ rs.description || '暂无描述' }}</span>
                    <span class="rt-skill-path">{{ skillPath(rs) }}</span>
                  </span>
                </label>
              </li>
            </ul>

            <div v-if="soleSelectedSkill" class="rt-import-meta">
              <label>
                工作区里的 skill 名称
                <input v-model="importName" type="text" autocomplete="off" />
              </label>
              <label>
                描述
                <textarea v-model="importDescription" rows="4" />
              </label>
            </div>
          </template>

          <p v-if="runtimeErr" class="error">{{ runtimeErr }}</p>

          <div class="rt-footer">
            <span class="rt-footer-hint">{{ runtimeFooterHint() }}</span>
            <button
              type="button"
              class="btn-add btn-import"
              :disabled="busy || !selectedCount"
              @click="promoteRuntimeSkills"
            >
              <span aria-hidden="true">⇩</span>
              {{ busy ? '导入中…' : '导入到工作区' }}
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
.modal-wide {
  width: min(640px, 100%);
  max-height: min(92vh, 820px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.modal-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 14px;
  flex-shrink: 0;
}
.modal-head-main {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
}
.modal-head h3 { margin: 0; font-size: 18px; }
.modal-lead { margin: 6px 0 0; font-size: 13px; color: var(--muted); }
.back-icon {
  border: none;
  background: transparent;
  color: var(--text);
  padding: 2px 4px 0 0;
  font-size: 18px;
  line-height: 1.2;
  cursor: pointer;
  flex-shrink: 0;
}
.back-icon:hover { color: #2563eb; }
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
.form select,
.rt-label select,
.rt-search input {
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

.runtime-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  flex: 1;
  overflow: hidden;
}
.rt-label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 10px;
  flex-shrink: 0;
}
.rt-combo {
  position: relative;
}
.rt-combo-trigger {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 8px 10px;
  background: #fff;
  font: inherit;
  font-size: 13px;
  font-weight: 400;
  color: var(--text);
  cursor: pointer;
  text-align: left;
}
.rt-combo-trigger:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.rt-combo-trigger:hover:not(:disabled) {
  border-color: #d1d5db;
}
.rt-combo.open .rt-combo-trigger {
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12);
}
.rt-combo-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rt-combo-chev {
  color: #9ca3af;
  font-size: 12px;
  flex-shrink: 0;
}
.rt-combo-menu {
  position: absolute;
  z-index: 20;
  left: 0;
  right: 0;
  top: calc(100% + 4px);
  margin: 0;
  padding: 4px;
  list-style: none;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 10px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.12);
  max-height: 240px;
  overflow: auto;
}
.rt-combo-option {
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
  color: var(--text);
  cursor: pointer;
  text-align: left;
}
.rt-combo-option:hover,
.rt-combo-option.active {
  background: #f3f4f6;
}
.rt-combo-option.active {
  font-weight: 600;
}
.rt-card {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 12px;
  background: #fafafa;
  flex-shrink: 0;
}
.rt-card-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid var(--border);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.rt-card-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}
.rt-card-text strong { font-size: 13px; font-weight: 650; }
.rt-card-text small { font-size: 12px; color: var(--muted); }
.rt-status {
  font-size: 12px;
  font-weight: 600;
  color: #9ca3af;
  text-transform: lowercase;
}
.rt-status.online { color: #16a34a; }
.rt-loading { margin: 8px 0 12px; font-size: 13px; }
.rt-search {
  position: relative;
  margin-bottom: 10px;
  flex-shrink: 0;
}
.rt-search-icon {
  position: absolute;
  left: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: #9ca3af;
  font-size: 14px;
  pointer-events: none;
}
.rt-search input {
  width: 100%;
  padding-left: 30px;
  box-sizing: border-box;
}
.rt-select-all {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 500;
  margin: 0 0 8px;
  cursor: pointer;
  flex-shrink: 0;
  color: var(--text);
}
.rt-skills {
  list-style: none;
  margin: 0;
  padding: 0;
  border: 1px solid var(--border);
  border-radius: 10px;
  overflow: auto;
  flex: 1;
  min-height: 180px;
  max-height: min(42vh, 360px);
}
.rt-skills li { border-bottom: 1px solid var(--border); }
.rt-skills li:last-child { border-bottom: none; }
.rt-skill {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  margin: 0;
  font-weight: 400;
  cursor: pointer;
}
.rt-skill:hover { background: #f9fafb; }
.rt-skill > input { margin-top: 3px; flex-shrink: 0; }
.rt-skill-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
}
.rt-skill-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}
.rt-skill-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}
.rt-skill-title strong {
  font-size: 13px;
  font-weight: 650;
}
.rt-tag {
  display: inline-flex;
  font-size: 11px;
  font-weight: 600;
  padding: 1px 7px;
  border-radius: 999px;
  background: #f3f4f6;
  color: #6b7280;
}
.rt-files {
  font-size: 11px;
  font-weight: 700;
  color: var(--text);
  white-space: nowrap;
  flex-shrink: 0;
}
.rt-skill-desc {
  font-size: 12px;
  color: var(--muted);
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.rt-skill-path {
  font-size: 11px;
  color: #9ca3af;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rt-import-meta {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: #fafafa;
  flex-shrink: 0;
}
.rt-import-meta label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  margin: 0;
}
.rt-import-meta input,
.rt-import-meta textarea {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 8px 10px;
  font: inherit;
  font-size: 13px;
  font-weight: 400;
  color: var(--text);
  background: #fff;
  resize: vertical;
}
.rt-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
  flex-shrink: 0;
}
.rt-footer-hint {
  font-size: 13px;
  color: var(--muted);
}
.btn-import {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
</style>
