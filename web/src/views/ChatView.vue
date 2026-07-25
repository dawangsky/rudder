<script setup lang="ts">
/** Chat 页占位：后续实现左会话列表 + 新建下拉选 Agent。 */
</script>

<template>
  <section class="page">
    <header class="page-header">
      <h2>对话</h2>
      <button type="button" class="primary" disabled title="需先创建智能体">新建聊天</button>
    </header>
    <div class="chat-layout">
      <aside class="session-list">
        <p class="muted">暂无会话。请先到「智能体」创建 Agent，再新建聊天。</p>
      </aside>
      <div class="chat-main">
        <p class="muted">选择或新建会话后，将在此展示当前智能体与消息。</p>
      </div>
    </div>
  </section>
</template>
