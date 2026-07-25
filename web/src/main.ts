import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'
import './styles/main.css'

/** 挂载 Vue 应用：路由与全局样式。 */
createApp(App).use(router).mount('#app')
