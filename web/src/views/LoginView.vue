<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { apiFetch } from '@/lib/api'
import {
  findRememberedAccount,
  listRememberedAccountsRecentFirst,
  rememberAccount,
  type RememberedAccount,
} from '@/lib/rememberAuth'
import { syncDaemonWithDesktopLogin } from '@/lib/syncDaemonLogin'

/** 登录 / 注册页：对接 /api/auth/login 与 /api/auth/register。 */

type AuthResponse = {
  sessionToken: string
  user: { id: string; email: string; displayName: string }
  workspace: { id: string; name: string; slug: string }
}

const router = useRouter()
const route = useRoute()
const mode = ref<'login' | 'register'>('login')
const email = ref('')
const password = ref('')
const displayName = ref('')
const error = ref('')
const loading = ref(false)
const accounts = ref<RememberedAccount[]>([])
const accountMenuOpen = ref(false)
const emailWrap = ref<HTMLElement | null>(null)

const hasAccounts = computed(() => accounts.value.length > 0)

function refreshAccounts() {
  accounts.value = listRememberedAccountsRecentFirst()
}

function pickAccount(a: RememberedAccount) {
  email.value = a.email
  password.value = a.password
  accountMenuOpen.value = false
}

function onEmailInput() {
  // 邮箱变更时若匹配已存账号则自动填密码，否则清空密码避免串号
  const hit = findRememberedAccount(email.value)
  if (hit) {
    password.value = hit.password
  }
}

function onDocClick(ev: MouseEvent) {
  if (!accountMenuOpen.value) return
  const el = emailWrap.value
  if (el && !el.contains(ev.target as Node)) {
    accountMenuOpen.value = false
  }
}

onMounted(() => {
  refreshAccounts()
  document.addEventListener('click', onDocClick)
  // 切换账号：不回填当前账号，但保留下拉可选历史
  if (route.query.switch === '1') {
    email.value = ''
    password.value = ''
    return
  }
  // 退出登录：回填最近一条，方便一键登录
  const recent = accounts.value[0]
  if (recent) {
    email.value = recent.email
    password.value = recent.password
  }
})

onUnmounted(() => {
  document.removeEventListener('click', onDocClick)
})

async function onSubmit() {
  error.value = ''
  if (!email.value || !password.value) {
    error.value = '请输入邮箱和密码'
    return
  }
  loading.value = true
  try {
    const path = mode.value === 'login' ? '/api/auth/login' : '/api/auth/register'
    const body: Record<string, string> = {
      email: email.value,
      password: password.value,
    }
    if (mode.value === 'register' && displayName.value) {
      body.displayName = displayName.value
    }
    const data = await apiFetch<AuthResponse>(path, {
      method: 'POST',
      body: JSON.stringify(body),
    })
    // 默认记录账号（最多 6 条，FIFO）
    rememberAccount(email.value.trim(), password.value)
    refreshAccounts()
    sessionStorage.setItem('rudder_session_token', data.sessionToken)
    sessionStorage.setItem('rudder_user_email', data.user.email)
    sessionStorage.setItem('rudder_workspace_id', data.workspace.id)
    try {
      await syncDaemonWithDesktopLogin(email.value.trim(), password.value)
    } catch (e) {
      console.warn('Daemon 同步失败', e)
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/chat'
    await router.replace(redirect)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <form class="login-card" @submit.prevent="onSubmit">
      <h1>Rudder</h1>
      <p class="hint">{{ mode === 'login' ? '登录后进入对话（默认首页）' : '注册后自动创建默认工作区' }}</p>

      <div ref="emailWrap" class="email-field">
        <span class="label">邮箱</span>
        <div class="email-row">
          <input
            v-model="email"
            type="email"
            autocomplete="username"
            placeholder="name@example.com"
            @input="onEmailInput"
            @focus="accountMenuOpen = hasAccounts"
          />
          <button
            v-if="hasAccounts"
            type="button"
            class="account-toggle"
            :aria-expanded="accountMenuOpen"
            title="选择已保存账号"
            @click.stop="accountMenuOpen = !accountMenuOpen"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M6 9l6 6 6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
            </svg>
          </button>
        </div>
        <div v-if="accountMenuOpen && hasAccounts" class="account-menu" role="listbox">
          <button
            v-for="a in accounts"
            :key="a.email"
            type="button"
            class="account-item"
            role="option"
            @click="pickAccount(a)"
          >
            <span class="av">{{ (a.email[0] || '?').toUpperCase() }}</span>
            <span class="em">{{ a.email }}</span>
          </button>
        </div>
        <p v-if="hasAccounts" class="tip">已保存 {{ accounts.length }} / 6 个账号，可下拉切换</p>
      </div>

      <label>
        密码
        <input v-model="password" type="password" autocomplete="current-password" />
      </label>
      <label v-if="mode === 'register'">
        显示名（可选）
        <input v-model="displayName" type="text" autocomplete="nickname" />
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit" class="primary" :disabled="loading">
        {{ loading ? '请稍候…' : mode === 'login' ? '登录' : '注册' }}
      </button>
      <button type="button" class="linkish" @click="mode = mode === 'login' ? 'register' : 'login'">
        {{ mode === 'login' ? '没有账号？去注册' : '已有账号？去登录' }}
      </button>
    </form>
  </div>
</template>

<style scoped>
.linkish {
  border: none;
  background: transparent;
  color: var(--accent);
  cursor: pointer;
  padding: 4px 0;
  text-align: left;
}
.email-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  position: relative;
  margin-bottom: 4px;
}
.email-field .label {
  font-size: 13px;
  color: var(--muted, #666);
}
.email-row {
  display: flex;
  align-items: stretch;
  gap: 0;
  border: 1px solid var(--border, #e5e7eb);
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}
.email-row:focus-within {
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}
.email-row input {
  border: none !important;
  box-shadow: none !important;
  border-radius: 0 !important;
  flex: 1;
  min-width: 0;
}
.account-toggle {
  border: none;
  border-left: 1px solid var(--border, #e5e7eb);
  background: #f9fafb;
  width: 40px;
  cursor: pointer;
  color: var(--muted, #6b7280);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.account-toggle:hover {
  background: #f3f4f6;
  color: var(--text, #111827);
}
.account-menu {
  position: absolute;
  left: 0;
  right: 0;
  top: calc(100% - 2px);
  z-index: 20;
  background: #fff;
  border: 1px solid var(--border, #e5e7eb);
  border-radius: 10px;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.12);
  padding: 4px;
  max-height: 220px;
  overflow: auto;
}
.account-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  border: none;
  background: transparent;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  text-align: left;
  font-size: 13px;
}
.account-item:hover {
  background: #f3f4f6;
}
.av {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #ea580c;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.em {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tip {
  margin: 0;
  font-size: 12px;
  color: var(--muted, #9ca3af);
}
</style>
