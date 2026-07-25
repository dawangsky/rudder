<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'

/** 登录页骨架：后续对接 /api/auth/login 与注册。 */

const router = useRouter()
const route = useRoute()
const email = ref('')
const password = ref('')
const error = ref('')

async function onSubmit() {
  error.value = ''
  if (!email.value || !password.value) {
    error.value = '请输入邮箱和密码'
    return
  }
  // TODO: 调用真实登录 API；脚手架阶段写入占位 token 便于预览壳子
  sessionStorage.setItem('rudder_session_token', 'dev-placeholder-token')
  sessionStorage.setItem('rudder_user_email', email.value)
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/chat'
  await router.replace(redirect)
}
</script>

<template>
  <div class="login-page">
    <form class="login-card" @submit.prevent="onSubmit">
      <h1>Rudder</h1>
      <p class="hint">登录后进入对话（默认首页）</p>
      <label>
        邮箱
        <input v-model="email" type="email" autocomplete="username" />
      </label>
      <label>
        密码
        <input v-model="password" type="password" autocomplete="current-password" />
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit">登录</button>
    </form>
  </div>
</template>
