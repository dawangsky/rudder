<script setup lang="ts">
/** 「⋯」菜单与操作按钮用的线性小图标。 */
withDefaults(
  defineProps<{
    name:
      | 'archive'
      | 'restore'
      | 'delete'
      | 'detail'
      | 'settings'
      | 'chat'
      | 'assign'
    size?: number
  }>(),
  { size: 14 },
)
</script>

<template>
  <svg
    class="action-ico"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    aria-hidden="true"
  >
    <!-- 归档 -->
    <template v-if="name === 'archive'">
      <path d="M4 7h16v12a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7z" stroke="currentColor" stroke-width="1.6" />
      <path d="M3 5h18v2H3V5z" stroke="currentColor" stroke-width="1.6" />
      <path d="M10 12h4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
    </template>
    <!-- 恢复 -->
    <template v-else-if="name === 'restore'">
      <path d="M4 12a8 8 0 1 0 2.3-5.7" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
      <path d="M4 5v5h5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
    </template>
    <!-- 删除 -->
    <template v-else-if="name === 'delete'">
      <path d="M5 8h14" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
      <path d="M9 8V6a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" stroke="currentColor" stroke-width="1.6" />
      <path d="M7 8l1 12h8l1-12" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" />
      <path d="M10 11v6M14 11v6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
    </template>
    <!-- 查看详情 -->
    <template v-else-if="name === 'detail'">
      <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.6" />
      <path d="M2.5 12S6 6 12 6s9.5 6 9.5 6-3.5 6-9.5 6S2.5 12 2.5 12z" stroke="currentColor" stroke-width="1.6" />
    </template>
    <!-- 设置 -->
    <template v-else-if="name === 'settings'">
      <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.6" />
      <path
        d="M12 3.5v2.2M12 18.3v2.2M4.9 6.5l1.6 1.6M17.5 15.9l1.6 1.6M3.5 12h2.2M18.3 12h2.2M4.9 17.5l1.6-1.6M17.5 8.1l1.6-1.6"
        stroke="currentColor"
        stroke-width="1.6"
        stroke-linecap="round"
      />
    </template>
    <!-- 私信 -->
    <template v-else-if="name === 'chat'">
      <path
        d="M5 6h14a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H9l-4 3v-3H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1z"
        stroke="currentColor"
        stroke-width="1.6"
        stroke-linejoin="round"
      />
    </template>
    <!-- 分配工作 -->
    <template v-else-if="name === 'assign'">
      <path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
    </template>
  </svg>
</template>

<style scoped>
.action-ico {
  flex-shrink: 0;
  display: block;
}
</style>
