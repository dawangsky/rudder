<script setup lang="ts">
/**
 * 新建智能体：选择起点 → 空白表单（AI 创建二期灰显推荐卡）。
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiFetch } from '@/lib/api'
import ProviderIcon from '@/components/ProviderIcon.vue'
import { PROVIDERS, baseProviderOf, displayName, type Runtime } from '@/lib/runtimes'

const route = useRoute()
const router = useRouter()

const step = computed(() => (route.name === 'agent-create-blank' ? 'blank' : 'start'))

const runtimes = ref<Runtime[]>([])
const skills = ref<{ id: string; name: string }[]>([])
const err = ref('')
const saving = ref(false)

const form = ref({
  name: '',
  description: '',
  provider: 'cursor',
  runtimeId: '' as string,
  instructions: '',
  skillIds: [] as string[],
})

const onlineRuntimes = computed(() =>
  runtimes.value.filter((r) => r.status === 'online'),
)

const providerChoices = computed(() => {
  const online = new Set(onlineRuntimes.value.map((r) => r.provider))
  return PROVIDERS.filter((p) => p.value !== 'stub' || online.has('stub')).map((p) => ({
    ...p,
    online: online.has(p.value),
  }))
})

const runtimeOptions = computed(() =>
  onlineRuntimes.value.filter((r) => r.provider === form.value.provider),
)

async function load() {
  try {
    runtimes.value = await apiFetch('/api/runtimes')
    skills.value = await apiFetch('/api/skills')
    // 默认选第一个在线 provider
    const first = onlineRuntimes.value[0]
    if (first) {
      form.value.provider = first.provider
      form.value.runtimeId = first.id
    }
  } catch (e) {
    err.value = e instanceof Error ? e.message : '加载失败'
  }
}

function goStart() {
  router.push({ name: 'agent-create' })
}

function goBlank() {
  err.value = ''
  router.push({ name: 'agent-create-blank' })
}

function goList() {
  router.push({ name: 'agents' })
}

function onProviderChange() {
  const opts = runtimeOptions.value
  form.value.runtimeId = opts[0]?.id || ''
}

async function create() {
  err.value = ''
  if (!form.value.name.trim()) {
    err.value = '请填写名称'
    return
  }
  if (!form.value.provider) {
    err.value = '请选择 Provider'
    return
  }
  saving.value = true
  try {
    // 若选了具体 runtime（含自定义），以该 runtime 的 provider 为准
    const rt = runtimeOptions.value.find((r) => r.id === form.value.runtimeId)
    const provider = rt?.provider || form.value.provider
    await apiFetch('/api/agents', {
      method: 'POST',
      body: JSON.stringify({
        name: form.value.name.trim(),
        description: form.value.description.trim(),
        provider,
        runtimeId: form.value.runtimeId || undefined,
        instructions: form.value.instructions,
        skillIds: form.value.skillIds,
      }),
    })
    await router.replace({ name: 'agents' })
  } catch (e) {
    err.value = e instanceof Error ? e.message : '创建失败'
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="page">
    <header class="crumb-head">
      <button type="button" class="back" aria-label="返回" @click="step === 'blank' ? goStart() : goList()">
        ←
      </button>
      <div>
        <h2>创建智能体</h2>
        <p class="sub">{{ step === 'start' ? '选择起点' : '空白配置' }}</p>
      </div>
    </header>

    <!-- 选择起点 -->
    <div v-if="step === 'start'" class="start">
      <h3 class="hero-title">你想从哪里开始?</h3>
      <p class="hero-lead">
        从空白配置、基于成熟模板修改，或者直接描述需求，通过对话完成创建。
      </p>
      <div class="cards">
        <button type="button" class="card" @click="goBlank">
          <span class="card-ico" aria-hidden="true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
              <path d="M7 4h7l3 3v13a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z" stroke="currentColor" stroke-width="1.6" />
              <path d="M14 4v4h4" stroke="currentColor" stroke-width="1.6" />
              <path d="M9 12h6M9 16h4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            </svg>
          </span>
          <strong>从空白开始</strong>
          <p>自己配置每个字段。适合已经明确知道智能体应该如何工作的用户。</p>
          <span class="cont">继续 ›</span>
        </button>

        <div class="card card-disabled" title="二期">
          <span class="badge">推荐</span>
          <span class="card-ico" aria-hidden="true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
              <path d="M5 6h14a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H9l-4 3v-3H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1z" stroke="currentColor" stroke-width="1.6" />
            </svg>
          </span>
          <strong>通过 AI 创建</strong>
          <p>描述你想要的结果。Agent Builder 会提出关键问题并实时生成草稿。</p>
          <span class="cont muted">即将支持</span>
        </div>
      </div>
    </div>

    <!-- 空白表单 -->
    <div v-else class="blank">
      <p v-if="!onlineRuntimes.length" class="warn">
        当前没有在线运行时。请先到「运行时」确认 Cursor / Claude / Codex 已探测并在线。
      </p>

      <label class="field">
        名称
        <input v-model="form.name" type="text" placeholder="例如：前端工程师" />
      </label>
      <label class="field">
        简介
        <input v-model="form.description" type="text" placeholder="一句话说明这个智能体做什么" />
      </label>

      <div class="field">
        <span class="label">Provider</span>
        <div class="provider-grid">
          <button
            v-for="p in providerChoices"
            :key="p.value"
            type="button"
            class="p-card"
            :class="{ on: form.provider === p.value, off: !p.online }"
            :disabled="!p.online"
            :title="p.online ? p.label : `${p.label} 未在线`"
            @click="form.provider = p.value; onProviderChange()"
          >
            <ProviderIcon :provider="p.value" :size="22" />
            <span>{{ p.short }}</span>
          </button>
        </div>
      </div>

      <label class="field">
        运行时
        <select v-model="form.runtimeId" :disabled="!runtimeOptions.length">
          <option disabled value="">请选择在线运行时</option>
          <option v-for="r in runtimeOptions" :key="r.id" :value="r.id">
            {{ displayName(r) }}{{ r.kind === 'custom' || r.provider.startsWith('custom_') ? ' · 自定义' : '' }}
            ({{ r.hostName || r.daemonId || r.id }})
          </option>
        </select>
      </label>

      <label class="field">
        Instructions
        <textarea
          v-model="form.instructions"
          rows="6"
          placeholder="限定领域、工作方式、输出要求…"
        />
      </label>

      <label class="field">
        挂载 Skills（可选，按住 Cmd/Ctrl 多选）
        <select v-model="form.skillIds" multiple size="4">
          <option v-for="s in skills" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>
      </label>

      <p v-if="err" class="error">{{ err }}</p>
      <div class="actions">
        <button type="button" class="mini" :disabled="saving" @click="goStart">上一步</button>
        <button
          type="button"
          class="btn-dark"
          :disabled="saving || !onlineRuntimes.length"
          @click="create"
        >
          {{ saving ? '创建中…' : '创建智能体' }}
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.page { max-width: 880px; }
.crumb-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 28px;
}
.back {
  width: 34px;
  height: 34px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
}
.back:hover { background: #f9fafb; }
h2 { margin: 0; font-size: 22px; }
.sub { margin: 4px 0 0; font-size: 13px; color: var(--muted); }

.start { padding-top: 24px; }
.hero-title {
  margin: 0 0 10px;
  font-size: 28px;
  font-weight: 700;
  text-align: center;
}
.hero-lead {
  margin: 0 auto 28px;
  max-width: 520px;
  text-align: center;
  font-size: 14px;
  color: var(--muted);
  line-height: 1.55;
}
.cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.card {
  position: relative;
  text-align: left;
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 14px;
  padding: 22px 20px 18px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 200px;
}
.card:hover:not(.card-disabled) {
  border-color: #c7ccd6;
  background: #fafafa;
}
.card-disabled {
  cursor: not-allowed;
  opacity: 0.72;
}
.badge {
  position: absolute;
  top: 14px;
  right: 14px;
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 999px;
  background: #111827;
  color: #fff;
}
.card-ico {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: #f3f4f6;
  color: #374151;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.card strong { font-size: 16px; }
.card p {
  margin: 0;
  font-size: 13px;
  color: var(--muted);
  line-height: 1.5;
  flex: 1;
}
.cont {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}
.cont.muted { color: var(--muted); font-weight: 500; }

.blank {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 20px;
}
.warn {
  margin: 0 0 14px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fff7ed;
  color: #9a3412;
  font-size: 13px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
  font-size: 13px;
}
.label { font-size: 13px; }
.field input,
.field select,
.field textarea {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 9px 10px;
  font: inherit;
}
.provider-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}
.p-card {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 10px;
  padding: 10px;
  cursor: pointer;
  font-size: 13px;
}
.p-card.on {
  border-color: #111827;
  background: #f9fafb;
  font-weight: 600;
}
.p-card.off { opacity: 0.4; cursor: not-allowed; }
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}
.mini {
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 8px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 13px;
}
.btn-dark {
  border: none;
  background: #1c2333;
  color: #fff;
  border-radius: 8px;
  padding: 8px 14px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
}
.btn-dark:disabled { opacity: 0.5; cursor: not-allowed; }
.error { color: var(--danger); margin: 0 0 10px; }

@media (max-width: 720px) {
  .cards { grid-template-columns: 1fr; }
  .provider-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
