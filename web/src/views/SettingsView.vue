<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getServerBaseUrl, setServerBaseUrl } from '@/lib/config'

/** 设置页：可配置 Self-Host Server 地址。 */

const serverUrl = ref('')
const saved = ref(false)

onMounted(() => {
  serverUrl.value = getServerBaseUrl()
})

function save() {
  setServerBaseUrl(serverUrl.value.trim())
  saved.value = true
}
</script>

<template>
  <section class="page">
    <header class="page-header"><h2>设置</h2></header>
    <label class="field">
      Server 地址（Self-Host）
      <input v-model="serverUrl" type="url" placeholder="http://127.0.0.1:8080" />
    </label>
    <button type="button" class="primary" @click="save">保存</button>
    <p v-if="saved" class="ok">已保存到本地。</p>
  </section>
</template>
