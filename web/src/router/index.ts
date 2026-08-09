import { createRouter, createWebHistory } from 'vue-router'
import ShellLayout from '../layouts/ShellLayout.vue'
import LoginView from '../views/LoginView.vue'
import OnboardingView from '../views/OnboardingView.vue'
import ChatView from '../views/ChatView.vue'
import IssuesView from '../views/IssuesView.vue'
import AgentsView from '../views/AgentsView.vue'
import AgentCreateView from '../views/AgentCreateView.vue'
import AgentDetailView from '../views/AgentDetailView.vue'
import SkillsView from '../views/SkillsView.vue'
import SkillDetailView from '../views/SkillDetailView.vue'
import RuntimesView from '../views/RuntimesView.vue'
import RuntimeMachineView from '../views/RuntimeMachineView.vue'
import RuntimeDetailView from '../views/RuntimeDetailView.vue'
import InboxView from '../views/InboxView.vue'
import SettingsView from '../views/SettingsView.vue'
import ProjectsView from '../views/ProjectsView.vue'
import { getSessionToken, hasWorkspace } from '../lib/session'

/**
 * 路由：登录后默认 Chat；无工作区必须先完成引导。
 */
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    {
      path: '/onboarding',
      name: 'onboarding',
      component: OnboardingView,
      meta: { onboarding: true },
    },
    {
      path: '/',
      component: ShellLayout,
      meta: { requiresWorkspace: true },
      children: [
        { path: '', redirect: '/chat' },
        { path: 'chat', name: 'chat', component: ChatView },
        { path: 'issues', name: 'issues', component: IssuesView },
        { path: 'projects', name: 'projects', component: ProjectsView },
        { path: 'agents', name: 'agents', component: AgentsView },
        { path: 'agents/new', name: 'agent-create', component: AgentCreateView },
        { path: 'agents/new/blank', name: 'agent-create-blank', component: AgentCreateView },
        { path: 'agents/:agentId', name: 'agent-detail', component: AgentDetailView },
        { path: 'skills', name: 'skills', component: SkillsView },
        { path: 'skills/:skillId', name: 'skill-detail', component: SkillDetailView },
        { path: 'runtimes', name: 'runtimes', component: RuntimesView },
        { path: 'runtimes/machines/:daemonId', name: 'runtime-machine', component: RuntimeMachineView },
        { path: 'runtimes/:runtimeId', name: 'runtime-detail', component: RuntimeDetailView },
        { path: 'inbox', name: 'inbox', component: InboxView },
        { path: 'settings', redirect: '/settings/daemon' },
        { path: 'settings/:section', name: 'settings', component: SettingsView },
      ],
    },
  ],
})

router.beforeEach((to) => {
  if (to.meta.public) return true
  const token = getSessionToken()
  if (!token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  const onboard = !!to.meta.onboarding
  const ws = hasWorkspace()
  if (!ws && !onboard) {
    return { name: 'onboarding' }
  }
  if (ws && onboard) {
    return { name: 'chat' }
  }
  return true
})
