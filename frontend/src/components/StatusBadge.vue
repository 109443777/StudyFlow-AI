<script setup lang="ts">
const props = defineProps<{
  status?: string
}>()

const statusMap: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' | 'primary' }> = {
  SUCCESS: { label: '成功', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  RUNNING: { label: '运行中', type: 'warning' },
  PARSING: { label: '解析中', type: 'warning' },
  QUEUED: { label: '排队中', type: 'info' },
  INIT: { label: '初始化', type: 'info' },
  UPLOADED: { label: '已上传', type: 'primary' },
  DOCUMENT: { label: '文档', type: 'primary' },
  PPT: { label: 'PPT', type: 'warning' },
  AUDIO: { label: '音频', type: 'success' },
  VIDEO: { label: '视频', type: 'danger' },
  TEXT: { label: '文本', type: 'info' },
}

const meta = statusMap[props.status ?? ''] ?? { label: props.status ?? '未知', type: 'info' as const }
</script>

<template>
  <el-tag :type="meta.type" round>{{ meta.label }}</el-tag>
</template>
