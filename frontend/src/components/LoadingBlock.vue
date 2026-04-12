<script setup lang="ts">
import { Loading } from '@element-plus/icons-vue'

defineProps<{
  loading?: boolean
  error?: string
  empty?: boolean
  emptyText?: string
}>()

const emit = defineEmits<{
  retry: []
}>()
</script>

<template>
  <div v-if="loading" class="state-block">
    <el-icon class="is-loading"><Loading /></el-icon>
    <span>正在加载，请稍候...</span>
  </div>
  <div v-else-if="error" class="state-block error">
    <span>{{ error }}</span>
    <el-button type="primary" link @click="emit('retry')">重试</el-button>
  </div>
  <div v-else-if="empty" class="state-block">
    <span>{{ emptyText || '暂无数据' }}</span>
  </div>
  <slot v-else />
</template>

<style scoped>
.state-block {
  display: flex;
  min-height: 160px;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--sf-muted);
}

.error {
  color: var(--sf-red);
}
</style>
