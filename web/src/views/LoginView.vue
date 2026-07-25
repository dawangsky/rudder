<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { apiFetch } from '@/lib/api'
import {
  clearRememberedAuth,
  loadRememberedAuth,
  saveRememberedAuth,
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
/** 记住账号密码（写入 localStorage） */
const remember = ref(false)
const error = ref('')
const loading = ref(false)

onMounted(() => {
  // 切换账号进入时不回填旧账号
  if (route.query.switch === '1') {
    email.value = ''
    password.value = ''
    remember.value = false
    return
  }
  const saved = loadRememberedAuth()
  if (saved) {
    email.value = saved.email
    password.value = saved.password
    remember.value = true
  }
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
    if (remember.value) {
      saveRememberedAuth(email.value.trim(), password.value)
    } else {
      clearRememberedAuth()
    }
    sessionStorage.setItem('rudder_session_token', data.sessionToken)
    sessionStorage.setItem('rudder_user_email', data.user.email)
    sessionStorage.setItem('rudder_workspace_id', data.workspace.id)
    // Desktop：本机只跑一个 Daemon，登录时自动绑定同一账号并重启（失败不阻断进入）
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
      <label>
        邮箱
        <input v-model="email" type="email" autocomplete="username" />
      </label>
      <label>
        密码
        <input v-model="password" type="password" autocomplete="current-password" />
      </label>
      <label v-if="mode === 'register'">
        显示名（可选）
        <input v-model="displayName" type="text" autocomplete="nickname" />
      </label>
      <label class="remember">
        <input v-model="remember" type="checkbox" />
        记住账号密码
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
.remember {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--muted, #666);
}
.remember input {
  width: auto;
  margin: 0;
}
</style>
