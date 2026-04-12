<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import type { UploadRequestOptions } from 'element-plus'

import { studyflowApi } from '@/api/studyflow'
import LoadingBlock from '@/components/LoadingBlock.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import type { MaterialVO, ParseTaskVO } from '@/types/api'
import { buildMaterialStage } from '@/utils/materialStatus'
import { getDisplayError } from '@/utils/result'

const router = useRouter()
const materials = ref<MaterialVO[]>([])
const tasksByMaterial = ref<Record<string, ParseTaskVO[]>>({})
const loading = ref(false)
const uploading = ref(false)
const uploadProgress = ref(0)
const error = ref('')

function formatSize(value: string) {
  const size = Number(value)
  if (!Number.isFinite(size)) {
    return value
  }
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

async function loadMaterials() {
  loading.value = true
  error.value = ''
  try {
    materials.value = await studyflowApi.listMaterials()
    await Promise.all(materials.value.slice(0, 8).map(async (item) => {
      tasksByMaterial.value[item.id] = await studyflowApi.listParseTasks(item.id)
    }))
  } catch (err) {
    error.value = getDisplayError(err)
  } finally {
    loading.value = false
  }
}

async function upload(options: UploadRequestOptions) {
  const file = options.file
  uploading.value = true
  uploadProgress.value = 0
  try {
    const material = await studyflowApi.uploadMaterial(file, (percentage) => {
      uploadProgress.value = percentage
    })
    ElMessage.success('上传成功，系统已自动进入异步解析队列')
    materials.value = [material, ...materials.value]
    tasksByMaterial.value[material.id] = await studyflowApi.listParseTasks(material.id)
    options.onSuccess?.(material)
  } catch (err) {
    const message = getDisplayError(err)
    ElMessage.error(message)
    const uploadError = new Error(message) as Parameters<NonNullable<typeof options.onError>>[0]
    options.onError?.(uploadError)
  } finally {
    uploading.value = false
  }
}

function latestTask(materialId: string) {
  return tasksByMaterial.value[materialId]?.[0]
}

function stageOf(material: MaterialVO) {
  return buildMaterialStage(material, tasksByMaterial.value[material.id] || [])
}

onMounted(loadMaterials)
</script>

<template>
  <section class="page-card material-page">
    <div class="toolbar">
      <div>
        <h2 class="section-title">资料中心</h2>
        <p class="muted">支持 PDF、PPT/PPTX、Word/DOCX、TXT、Markdown、MP3、WAV、MP4。</p>
      </div>
      <el-button :loading="loading" @click="loadMaterials">刷新列表</el-button>
    </div>

    <el-upload
      drag
      action="#"
      :http-request="upload"
      :show-file-list="false"
      :disabled="uploading"
      accept=".pdf,.ppt,.pptx,.doc,.docx,.txt,.md,.markdown,.mp3,.wav,.mp4"
      class="upload-box"
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">拖拽文件到这里，或 <em>点击上传</em></div>
      <template #tip>
        <div class="el-upload__tip">大文件建议后续使用分片上传；当前页面先覆盖普通上传主链路。</div>
      </template>
    </el-upload>

    <el-progress v-if="uploading" :percentage="uploadProgress" :stroke-width="12" class="upload-progress" />

    <LoadingBlock :loading="loading" :error="error" :empty="materials.length === 0" empty-text="还没有上传资料" @retry="loadMaterials">
      <el-table :data="materials" class="material-table">
        <el-table-column label="文件名" min-width="280">
          <template #default="{ row }">
            <strong>{{ row.fileName }}</strong>
            <div class="muted small">{{ row.fileType }} · {{ formatSize(row.fileSize) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="资料类型" width="110">
          <template #default="{ row }"><StatusBadge :status="row.materialType" /></template>
        </el-table-column>
        <el-table-column label="解析状态" width="130">
          <template #default="{ row }"><StatusBadge :status="row.parseStatus" /></template>
        </el-table-column>
        <el-table-column label="当前进度" min-width="260">
          <template #default="{ row }">
            <div class="stage-cell">
              <strong>{{ stageOf(row).label }}</strong>
              <span class="muted small">{{ stageOf(row).detail }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="最新任务" min-width="170">
          <template #default="{ row }">
            <template v-if="latestTask(row.id)">
              <StatusBadge :status="latestTask(row.id)?.status" />
              <span class="task-name">{{ latestTask(row.id)?.taskType }}</span>
            </template>
            <span v-else class="muted">暂无任务</span>
          </template>
        </el-table-column>
        <el-table-column label="上传时间" prop="createTime" width="180" />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="router.push(`/materials/${row.id}`)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </LoadingBlock>
  </section>
</template>

<style scoped>
.material-page {
  padding: 26px;
}

.upload-box {
  margin-bottom: 18px;
}

.upload-progress {
  margin-bottom: 18px;
}

.material-table {
  width: 100%;
}

.small {
  margin-top: 5px;
  font-size: 12px;
}

.task-name {
  margin-left: 8px;
  color: var(--sf-muted);
  font-size: 12px;
}

.stage-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
</style>
