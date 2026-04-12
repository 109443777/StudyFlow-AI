<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { studyflowApi } from '@/api/studyflow'
import LoadingBlock from '@/components/LoadingBlock.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import type { HealthCheckVO, MaterialVO } from '@/types/api'
import { getDisplayError } from '@/utils/result'

const health = ref<HealthCheckVO>()
const materials = ref<MaterialVO[]>([])
const loading = ref(false)
const error = ref('')

async function loadDashboard() {
  loading.value = true
  error.value = ''
  try {
    const [healthResult, materialResult] = await Promise.all([studyflowApi.health(), studyflowApi.listMaterials()])
    health.value = healthResult
    materials.value = materialResult
  } catch (err) {
    error.value = getDisplayError(err)
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)
</script>

<template>
  <LoadingBlock :loading="loading" :error="error" @retry="loadDashboard">
    <section class="dashboard-grid">
      <article class="metric page-card">
        <p>后端服务</p>
        <h2>{{ health?.status || '未知' }}</h2>
        <StatusBadge :status="health?.status === 'UP' ? 'SUCCESS' : 'FAILED'" />
      </article>
      <article class="metric page-card">
        <p>资料数量</p>
        <h2>{{ materials.length }}</h2>
        <span class="muted">当前账号已上传资料</span>
      </article>
      <article class="metric page-card">
        <p>AI 模型</p>
        <h2>{{ health?.chatModel || '-' }}</h2>
        <span class="muted">{{ health?.aiProvider || '未配置' }}</span>
      </article>
      <article class="metric page-card">
        <p>Embedding</p>
        <h2>{{ health?.embeddingModel || '-' }}</h2>
        <span class="muted">{{ health?.embeddingProvider || '未配置' }}</span>
      </article>
    </section>

    <section class="page-card quick-start">
      <div>
        <h2>推荐测试路径</h2>
        <p>先上传一份 TXT 或 PDF，等待解析任务完成，再进入资料问答页提问。</p>
      </div>
      <div class="quick-actions">
        <el-button type="primary" @click="$router.push('/materials')">上传资料</el-button>
        <el-button @click="$router.push('/qa')">进入问答</el-button>
      </div>
    </section>
  </LoadingBlock>
</template>

<style scoped>
.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.metric {
  padding: 22px;
}

.metric p {
  margin: 0 0 8px;
  color: var(--sf-muted);
}

.metric h2 {
  margin: 0 0 12px;
  font-size: 28px;
}

.quick-start {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 26px;
}

.quick-start h2 {
  margin: 0 0 8px;
}

.quick-start p {
  margin: 0;
  color: var(--sf-muted);
}

.quick-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

@media (max-width: 1100px) {
  .dashboard-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .quick-start {
    flex-direction: column;
  }
}
</style>
