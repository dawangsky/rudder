import { createRouter, createWebHistory } from 'vue-router'
import ShellLayout from '../layouts/ShellLayout.vue'
import LoginView from '../views/LoginView.vue'
import ChatView from '../views/ChatView.vue'
import IssuesView from '../views/IssuesView.vue'
import AgentsView from '../views/AgentsView.vue'
import SkillsView from '../views/SkillsView.vue'
import RuntimesView from '../views/RuntimesView.vue'
import InboxView from '../views/InboxView.vue'
import SettingsView from '../views/SettingsView.vue'

/**
 * 路由：登录后默认进入 Chat（产品约定）。
 * MVP 暂用简单 sessionStorage 标记，后续对接真实 Token。
 */
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    {
      path: '/',
      component: ShellLayout,
      children: [
        { path: '', redirect: '/chat' },
        { path: 'chat', name: 'chat', component: ChatView },
        { path: 'issues', name: 'issues', component: IssuesView },
        { path: 'agents', name: 'agents', component: AgentsView },
        { path: 'skills', name: 'skills', component: SkillsView },
        { path: 'runtimes', name: 'runtimes', component: RuntimesView },
        { path: 'inbox', name: 'inbox', component: InboxView },
        { path: 'settings', name: 'settings', component: SettingsView },
      ],
    },
  ],
})

router.beforeEach((to) => {
  if (to.meta.public) return true
  const token = sessionStorage.getItem('rudder_session_token')
  if (!token) return { name: 'login', query: { redirect: to.fullPath } }
  return true
})
