<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { studyflowApi } from '@/api/studyflow'
import LoadingBlock from '@/components/LoadingBlock.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import type { MaterialContentVO, MaterialSummaryVO, MaterialVO, ParseTaskVO } from '@/types/api'
import { getDisplayError } from '@/utils/result'

const route = useRoute()
const router = useRouter()
const materialId = computed(() => String(route.params.id))

const material = ref<MaterialVO>()
const tasks = ref<ParseTaskVO[]>([])
const content = ref<MaterialContentVO>()
const summary = ref<MaterialSummaryVO>()
const loading = ref(false)
const actionLoading = ref(false)
const error = ref('')

const latestStatus = computed(() => tasks.value[0]?.status || material.value?.parseStatus)

async function safeLoad<T>(loader: () => Promise<T>, assign: (value: T) => void) {
  try {
    assign(await loader())
  } catch {
    // 子资源可能还没生成，详情页允许局部为空。
  }
}

async function loadDetail() {
  loading.value = true
  error.value = ''
  try {
    material.value = await studyflowApi.getMaterial(materialId.value)
    tasks.value = await studyflowApi.listParseTasks(materialId.value)
    await safeLoad(() => studyflowApi.getMaterialContent(materialId.value), (value) => { content.value = value })
    await safeLoad(() => studyflowApi.getMaterialSummary(materialId.value), (value) => { summary.value = value })
  } catch (err) {
    error.value = getDisplayError(err)
  } finally {
    loading.value = false
  }
}

async function dispatchParse() {
  actionLoading.value = true
  try {
    await studyflowApi.dispatchParse(materialId.value)
    ElMessage.success('解析任务已投递')
    await loadDetail()
  } catch (err) {
    ElMessage.warning(getDisplayError(err))
  } finally {
    actionLoading.value = false
  }
}

async function generateSummary() {
  actionLoading.value = true
  try {
    summary.value = await studyflowApi.generateMaterialSummary(materialId.value)
    ElMessage.success('摘要生成完成')
  } catch (err) {
    ElMessage.error(getDisplayError(err))
  } finally {
    actionLoading.value = false
  }
}

function openQa() {
  router.push({ path: '/qa', query: { materialId: materialId.value } })
}

onMounted(loadDetail)
</script>

<template>
  <LoadingBlock :loading="loading" :error="error" @retry="loadDetail">
    <section v-if="material" class="detail page-card">
      <div class="toolbar">
        <div>
          <h2 class="section-title">{{ material.fileName }}</h2>
          <p class="muted">{{ material.fileType }} · {{ material.createTime }}</p>
        </div>
        <div class="actions">
          <StatusBadge :status="latestStatus" />
          <el-button :loading="actionLoading" @click="dispatchParse">手动投递解析</el-button>
          <el-button type="primary" @click="openQa">基于资料提问</el-button>
        </div>
      </div>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="资料 ID">{{ material.id }}</el-descriptions-item>
        <el-descriptions-item label="资料类型">{{ material.materialType }}</el-descriptions-item>
        <el-descriptions-item label="对象 Key">{{ material.objectKey }}</el-descriptions-item>
        <el-descriptions-item label="上传状态">{{ material.uploadStatus }}</el-descriptions-item>
      </el-descriptions>

      <el-tabs class="detail-tabs">
        <el-tab-pane label="解析任务">
          <el-table :data="tasks">
            <el-table-column label="任务类型" prop="taskType" width="170" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }"><StatusBadge :status="row.status" /></template>
            </el-table-column>
            <el-table-column label="重试次数" prop="retryCount" width="100" />
            <el-table-column label="失败原因" prop="failReason" min-width="220" />
            <el-table-column label="更新时间" prop="updateTime" width="180" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="解析文本">
          <template v-if="content">
            <el-alert title="这里展示的是清洗后的文本，后续摘要和 RAG 问答都会基于这些内容继续处理。" type="info" show-icon :closable="false" />
            <div class="text-block content-box">{{ content.cleanedText }}</div>
          </template>
          <el-empty v-else description="解析文本还没有生成，等待 TEXT_PARSE 成功后再刷新。" />
        </el-tab-pane>

        <el-tab-pane label="AI 摘要">
          <template v-if="summary">
            <h3>摘要</h3>
            <p class="text-block">{{ summary.summaryText }}</p>
            <h3>关键词</h3>
            <el-space wrap>
              <el-tag v-for="keyword in summary.keywords" :key="keyword">{{ keyword }}</el-tag>
            </el-space>
            <h3>知识点</h3>
            <el-timeline>
              <el-timeline-item v-for="point in summary.keyPoints" :key="point">{{ point }}</el-timeline-item>
            </el-timeline>
            <h3>复习提纲</h3>
            <ol>
              <li v-for="item in summary.reviewOutline" :key="item">{{ item }}</li>
            </ol>
          </template>
          <el-empty v-else description="AI 摘要还没有生成，可以等待异步任务完成或手动触发。">
            <el-button type="primary" :loading="actionLoading" @click="generateSummary">生成 AI 摘要</el-button>
          </el-empty>
        </el-tab-pane>
      </el-tabs>
    </section>
  </LoadingBlock>
</template>

<style scoped>
.detail {
  padding: 26px;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.detail-tabs {
  margin-top: 22px;
}

.content-box {
  max-height: 520px;
  overflow: auto;
  margin-top: 16px;
  padding: 18px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
}
</style>
