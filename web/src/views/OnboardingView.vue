<script setup lang="ts">
/**
 * 新用户引导：欢迎 → 关于你 → 创建工作区。无工作区不可进入主应用。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiFetch } from '@/lib/api'
import { getSessionEmail, setWorkspaceId } from '@/lib/session'
import { syncDaemonWithDesktopLogin } from '@/lib/syncDaemonLogin'
import { findRememberedAccount } from '@/lib/rememberAuth'

const router = useRouter()
/** 0 欢迎 / 1 关于你 / 2 创建工作区 */
const step = ref(0)
const role = ref('')
const intent = ref('')
const wsName = ref('')
const creating = ref(false)
const err = ref('')
const checking = ref(true)

onMounted(async () => {
  // 会话丢了 workspace_id 但服务端已有工作区时，直接进入产品
  try {
    const me = await apiFetch<{
      workspace: { id: string } | null
      needsOnboarding?: boolean
    }>('/api/auth/me')
    if (me.workspace?.id) {
      setWorkspaceId(me.workspace.id)
      await router.replace({ name: 'chat' })
      return
    }
  } catch {
    /* 继续引导 */
  } finally {
    checking.value = false
  }
})

const ROLES = [
  { id: 'engineer', label: '工程师 / 开发者', icon: '</>' },
  { id: 'pm', label: '产品经理', icon: '▣' },
  { id: 'designer', label: '设计师', icon: '✎' },
  { id: 'founder', label: '创始人 / 高管', icon: '▲' },
  { id: 'growth', label: '市场 / 增长', icon: '◎' },
  { id: 'writer', label: '写作 / 内容', icon: '¶' },
  { id: 'researcher', label: '研究员 / 分析师', icon: '⌕' },
  { id: 'ops', label: '运营 / 项目管理', icon: '☰' },
  { id: 'student', label: '学生 / 个人使用', icon: '🎓' },
  { id: 'other', label: '其他', icon: '···' },
]

const INTENTS = [
  { id: 'code', label: '让 AI agent 帮我写代码', icon: '</>' },
  { id: 'team', label: '给团队做任务管理', icon: '☑' },
  { id: 'personal', label: '管理我自己的任务', icon: '☺' },
  { id: 'research', label: '计划、头脑风暴、调研', icon: '◉' },
  { id: 'publish', label: '写作、编辑、发布', icon: '▤' },
  { id: 'automate', label: '自动化日常运营', icon: '⚡' },
  { id: 'browse', label: '先逛逛看看', icon: '◎' },
  { id: 'other', label: '其他', icon: '···' },
]

const slug = computed(() => slugify(wsName.value))
const issuePrefix = computed(() => derivePrefix(slug.value))
const canCreate = computed(() => wsName.value.trim().length > 0)

function slugify(raw: string) {
  const s = raw
    .trim()
    .toLowerCase()
    .replace(/[\s_]+/g, '-')
    .replace(/[^a-z0-9\u4e00-\u9fff-]/g, '-')
    .replace(/-{2,}/g, '-')
    .replace(/^-|-$/g, '')
  if (/[\u4e00-\u9fff]/.test(s)) {
    const ascii = s.replace(/[\u4e00-\u9fff]+/g, 'ws').replace(/-{2,}/g, '-').replace(/^-|-$/g, '')
    if (!ascii || ascii === 'ws') return `ws-${(Math.abs(hash(raw)) % 0xffff).toString(16)}`
    return ascii
  }
  return s
}

function hash(s: string) {
  let h = 0
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0
  return h
}

function derivePrefix(s: string) {
  const letters = s.replace(/[^a-zA-Z0-9]/g, '').toUpperCase()
  if (letters.length >= 2) return letters.slice(0, Math.min(4, letters.length))
  return 'WS'
}

function back() {
  err.value = ''
  if (step.value > 0) step.value -= 1
}

function nextFromWelcome() {
  step.value = 1
}

function skipAbout() {
  step.value = 2
}

function continueAbout() {
  step.value = 2
}

async function createWorkspace() {
  if (!canCreate.value || creating.value) return
  err.value = ''
  creating.value = true
  try {
    const ws = await apiFetch<{ id: string; name: string; slug: string }>('/api/auth/workspaces', {
      method: 'POST',
      body: JSON.stringify({
        name: wsName.value.trim(),
        slug: slug.value || undefined,
        issuePrefix: issuePrefix.value,
        role: role.value || undefined,
        intent: intent.value || undefined,
      }),
    })
    setWorkspaceId(ws.id)
    // 尽量同步 Daemon（有记住密码时）
    const email = getSessionEmail()
    const saved = findRememberedAccount(email)
    if (email && saved?.rememberPassword && saved.password) {
      try {
        await syncDaemonWithDesktopLogin(email, saved.password)
      } catch (e) {
        console.warn('Daemon 同步失败', e)
      }
    }
    await router.replace({ name: 'chat' })
  } catch (e) {
    err.value = e instanceof Error ? e.message : '创建失败'
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <div v-if="checking" class="onboard checking">加载中…</div>
  <div v-else class="onboard">
    <!-- Step 0: 欢迎 -->
    <section v-if="step === 0" class="welcome">
      <div class="welcome-left">
        <div class="mark" aria-hidden="true">✶</div>
        <p class="eyebrow">欢迎使用 Rudder</p>
        <h1>
          你的 AI 队友，在
          <em>同一个工作区</em>。
        </h1>
        <p class="lead">
          像分配给同事一样把 task 交给它们——它们会接手、推进状态、完成后留下评论。
        </p>
        <p class="sub">完成本流程后，即可在工作区里创建智能体、发起对话与 issue。</p>
        <button type="button" class="btn-dark" @click="nextFromWelcome">开始探索 →</button>
      </div>
      <aside class="welcome-right">
        <p class="aside-lead">每个 issue、每条对话、每个决策——团队和智能体共享同一份上下文。</p>
        <div class="cards">
          <article class="msg">
            <span class="av you">你</span>
            <div>
              <header><strong>你</strong><span>RD-42</span></header>
              <p>@内容智能体 能不能起草一篇简短的发布文? 参考 @研究智能体 的访谈结论。</p>
            </div>
          </article>
          <article class="msg">
            <span class="av a">内</span>
            <div>
              <header><strong>内容智能体</strong><span>RD-42</span></header>
              <p>好的，正在拉取研究智能体的引述，围绕「节省的时间」展开…</p>
              <span class="st progress">进行中</span>
            </div>
          </article>
          <article class="msg">
            <span class="av b">研</span>
            <div>
              <header><strong>研究智能体</strong><span>RD-38</span></header>
              <p>本周用户访谈整理完成 —— 12 通电话、4 个反复出现的主题。</p>
              <span class="st done">已完成</span>
            </div>
          </article>
          <article class="msg">
            <span class="av c">审</span>
            <div>
              <header><strong>审稿智能体</strong><span>RD-42</span></header>
              <p>已审阅完那一版草稿 —— 留了 4 条语气批注。等新一版。</p>
              <span class="st review">审阅中</span>
            </div>
          </article>
        </div>
      </aside>
    </section>

    <!-- Step 1 / 2: 关于你 + 创建工作区 共用顶栏 -->
    <section v-else class="flow">
      <header class="flow-bar">
        <button type="button" class="back" @click="back">← 返回</button>
        <div class="dots" aria-hidden="true">
          <i :class="{ on: step >= 1 }" />
          <i :class="{ on: step >= 2 }" />
          <i :class="{ on: false }" />
        </div>
        <span class="step-label">第 {{ step }} 步 / 共 3 步</span>
      </header>

      <!-- 关于你 -->
      <div v-if="step === 1" class="about">
        <p class="eyebrow">关于你</p>
        <h2>简单介绍一下你自己。</h2>

        <div class="block">
          <h3><span>01</span> 你是什么角色?</h3>
          <div class="grid">
            <button
              v-for="r in ROLES"
              :key="r.id"
              type="button"
              class="chip"
              :class="{ on: role === r.id }"
              @click="role = r.id"
            >
              <span class="chip-ico">{{ r.icon }}</span>
              {{ r.label }}
            </button>
          </div>
        </div>

        <div class="block">
          <h3><span>02</span> 你打算用 Rudder 做什么?</h3>
          <div class="grid">
            <button
              v-for="it in INTENTS"
              :key="it.id"
              type="button"
              class="chip"
              :class="{ on: intent === it.id }"
              @click="intent = it.id"
            >
              <span class="chip-ico">{{ it.icon }}</span>
              {{ it.label }}
            </button>
          </div>
        </div>

        <footer class="flow-foot">
          <p class="hint">选一项继续——不想说也可以跳过。</p>
          <div class="foot-actions">
            <button type="button" class="link" @click="skipAbout">跳过</button>
            <button type="button" class="btn-dark" @click="continueAbout">继续 →</button>
          </div>
        </footer>
      </div>

      <!-- 创建工作区 -->
      <div v-else class="ws-step">
        <div class="ws-main">
          <p class="eyebrow">你的第一个工作区</p>
          <h2>给工作区起个名字。</h2>
          <p class="lead">
            工作区是 issue、智能体和项目所在的地方。之后可以邀请同事，或再开一个工作区。
          </p>

          <label class="field">
            工作区名称
            <input v-model="wsName" type="text" placeholder="Acme Inc、我的实验室、副业..." maxlength="64" />
          </label>

          <div class="url-row">
            <span class="url-label">URL</span>
            <div class="url-box">
              <span class="host">rudder.app/</span>
              <strong>{{ slug || '…' }}</strong>
            </div>
          </div>

          <p class="prefix-hint">
            issue 前缀将形如 <code>{{ issuePrefix }}-123</code>。之后可以在设置里修改。
          </p>

          <p v-if="err" class="error">{{ err }}</p>

          <footer class="flow-foot tight">
            <p class="hint">{{ canCreate ? '准备好了就可以创建。' : '先给工作区起个名字才能创建。' }}</p>
            <button
              type="button"
              class="btn-dark"
              :disabled="!canCreate || creating"
              @click="createWorkspace"
            >
              {{ creating ? '创建中…' : '创建工作区 →' }}
            </button>
          </footer>
        </div>

        <aside class="ws-aside">
          <h3>工作区里有什么</h3>
          <div class="preview">
            <div class="preview-head">
              <span class="av">R</span>
              <span>rudder / {{ slug || 'workspace' }}</span>
              <span class="lock">🔒</span>
            </div>
            <ul>
              <li><strong>收件箱</strong><span>你的通知</span></li>
              <li><strong>Issues</strong><span>共享任务面板</span></li>
              <li><strong>智能体</strong><span>你的 AI 队友</span></li>
              <li><strong>项目</strong><span>把相关 issue 分组</span></li>
              <li><strong>自动化</strong><span>定时自动化</span></li>
              <li><strong>运行时</strong><span>智能体跑的地方</span></li>
              <li><strong>Skills</strong><span>可复用的剧本</span></li>
            </ul>
          </div>
          <h3>你在这里会做什么</h3>
          <ul class="bullets">
            <li>像分配给同事一样把 issue 分配给智能体</li>
            <li>无需创建 issue，直接和任意智能体对话</li>
            <li>邀请同事 —— 他们只会看到这个工作区</li>
            <li>随时从左上角切换到其他工作区</li>
          </ul>
        </aside>
      </div>
    </section>
  </div>
</template>

<style scoped>
.onboard {
  min-height: 100%;
  background: #f6f7f9;
  color: #1c2333;
}
.checking {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  font-size: 14px;
}
.welcome {
  display: grid;
  grid-template-columns: 1fr 1fr;
  min-height: 100vh;
}
.welcome-left {
  padding: 72px 64px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  max-width: 560px;
}
.mark {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: #111827;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  margin-bottom: 20px;
}
.eyebrow {
  margin: 0 0 10px;
  font-size: 13px;
  color: #6b7280;
  font-weight: 600;
}
h1 {
  margin: 0 0 16px;
  font-size: 40px;
  line-height: 1.2;
  font-family: "Source Serif 4", "Noto Serif SC", Georgia, serif;
  font-weight: 650;
}
h1 em {
  font-style: normal;
  color: #2563eb;
}
.lead {
  margin: 0 0 12px;
  font-size: 16px;
  line-height: 1.6;
  color: #374151;
}
.sub {
  margin: 0 0 28px;
  font-size: 13px;
  color: #9ca3af;
  line-height: 1.5;
}
.btn-dark {
  align-self: flex-start;
  border: none;
  background: #111827;
  color: #fff;
  border-radius: 10px;
  padding: 12px 18px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
.btn-dark:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.welcome-right {
  background: #eef0f3;
  padding: 48px 40px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.aside-lead {
  font-size: 13px;
  color: #6b7280;
  font-style: italic;
  margin: 0 0 20px;
  line-height: 1.5;
}
.cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.msg {
  display: flex;
  gap: 10px;
  background: #fff;
  border-radius: 12px;
  padding: 12px 14px;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.06);
}
.msg .av {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #111827;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.msg .av.a { background: #7c3aed; }
.msg .av.b { background: #0891b2; }
.msg .av.c { background: #059669; }
.msg header {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 4px;
}
.msg header strong { color: #111827; }
.msg p { margin: 0; font-size: 13px; line-height: 1.45; }
.st {
  display: inline-block;
  margin-top: 6px;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
}
.st.progress { background: #fff7ed; color: #c2410c; }
.st.done { background: #eff6ff; color: #1d4ed8; }
.st.review { background: #ecfdf5; color: #047857; }

.flow {
  max-width: 1080px;
  margin: 0 auto;
  padding: 24px 28px 48px;
  min-height: 100vh;
}
.flow-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 28px;
}
.back {
  border: none;
  background: transparent;
  color: #6b7280;
  cursor: pointer;
  font-size: 13px;
  padding: 0;
}
.dots {
  display: flex;
  gap: 6px;
  flex: 1;
}
.dots i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d1d5db;
  display: block;
}
.dots i.on { background: #111827; }
.step-label {
  font-size: 12px;
  color: #9ca3af;
}
.about h2,
.ws-main h2 {
  margin: 0 0 24px;
  font-size: 28px;
  font-weight: 700;
}
.block { margin-bottom: 28px; }
.block h3 {
  margin: 0 0 12px;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.block h3 span {
  color: #9ca3af;
  font-weight: 600;
  font-size: 12px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}
.chip {
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 10px;
  padding: 12px 12px;
  text-align: left;
  cursor: pointer;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 48px;
}
.chip:hover { border-color: #cbd5e1; }
.chip.on {
  border-color: #111827;
  box-shadow: 0 0 0 1px #111827;
}
.chip-ico {
  color: #6b7280;
  font-size: 12px;
  width: 20px;
  text-align: center;
  flex-shrink: 0;
}
.flow-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-top: 32px;
  padding-top: 16px;
  border-top: 1px solid #e5e7eb;
}
.flow-foot.tight { border-top: none; padding-top: 8px; }
.hint { margin: 0; font-size: 13px; color: #9ca3af; }
.foot-actions { display: flex; gap: 10px; align-items: center; }
.link {
  border: none;
  background: transparent;
  color: #6b7280;
  cursor: pointer;
  font-size: 13px;
}

.ws-step {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 36px;
  align-items: start;
}
.ws-main .lead {
  margin: -12px 0 24px;
  font-size: 14px;
  color: #6b7280;
  line-height: 1.55;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 16px;
}
.field input {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 10px 12px;
  font: inherit;
  font-weight: 400;
  background: #fff;
}
.url-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  font-size: 13px;
}
.url-label { color: #6b7280; width: 36px; }
.url-box {
  flex: 1;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 10px 12px;
  background: #fff;
  color: #6b7280;
}
.url-box strong { color: #111827; }
.prefix-hint {
  font-size: 13px;
  color: #6b7280;
  margin: 0 0 16px;
}
.prefix-hint code {
  background: #f3f4f6;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
}
.error { color: #b42318; font-size: 13px; }
.ws-aside {
  background: #eef0f3;
  border-radius: 16px;
  padding: 20px;
}
.ws-aside h3 {
  margin: 0 0 12px;
  font-size: 14px;
}
.preview {
  background: #fff;
  border-radius: 12px;
  padding: 12px;
  margin-bottom: 20px;
  border: 1px solid #e5e7eb;
}
.preview-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  margin-bottom: 10px;
  color: #6b7280;
}
.preview-head .av {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: #111827;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
}
.preview ul,
.bullets {
  list-style: none;
  margin: 0;
  padding: 0;
}
.preview li {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  padding: 7px 0;
  border-top: 1px solid #f3f4f6;
}
.preview li span { color: #9ca3af; }
.bullets li {
  font-size: 13px;
  color: #374151;
  padding: 6px 0 6px 14px;
  position: relative;
  line-height: 1.45;
}
.bullets li::before {
  content: '•';
  position: absolute;
  left: 0;
  color: #9ca3af;
}

@media (max-width: 960px) {
  .welcome, .ws-step { grid-template-columns: 1fr; }
  .welcome-right { display: none; }
  .grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .welcome-left { padding: 40px 24px; }
}
</style>
