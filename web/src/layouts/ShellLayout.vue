<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { computed } from 'vue'

/** 左侧常驻导航壳：参考 Multica 控制台密度，中文浅色。 */

const route = useRoute()
const router = useRouter()

const navItems = [
  { path: '/chat', label: '对话' },
  { path: '/issues', label: '议题' },
  { path: '/agents', label: '智能体' },
  { path: '/skills', label: '技能' },
  { path: '/runtimes', label: '运行时' },
  { path: '/inbox', label: '收件箱' },
  { path: '/settings', label: '设置' },
]

const activePath = computed(() => route.path)

function go(path: string) {
  router.push(path)
}
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand">Rudder</div>
      <nav>
        <button
          v-for="item in navItems"
          :key="item.path"
          type="button"
          class="nav-item"
          :class="{ active: activePath.startsWith(item.path) }"
          @click="go(item.path)"
        >
          {{ item.label }}
        </button>
      </nav>
    </aside>
    <main class="content">
      <router-view />
    </main>
  </div>
</template>
