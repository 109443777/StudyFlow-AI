<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { RefreshRight, UploadFilled, VideoPause } from '@element-plus/icons-vue'
import type { UploadRequestOptions } from 'element-plus'

import { studyflowApi } from '@/api/studyflow'
import LoadingBlock from '@/components/LoadingBlock.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import type { MaterialVO, ParseTaskVO } from '@/types/api'
import { buildMaterialStage } from '@/utils/materialStatus'
import { buildMultipartPlan, calculateUploadedPercentage, createFileFingerprint } from '@/utils/multipartUpload'
import { getDisplayError } from '@/utils/result'

type MultipartStatus = 'IDLE' | 'INIT' | 'UPLOADING' | 'COMPLETING' | 'FAILED' | 'SUCCESS' | 'ABORTED'

interface MultipartState {
  uploadId: string
  materialId: string
  fileName: string
  fileSize: number
  partSize: number
  totalParts: number
  uploadedParts: number
  currentPart: number
  currentPartProgress: number
  percentage: number
  status: MultipartStatus
  error: string
}

const router = useRouter()
const materials = ref<MaterialVO[]>([])
const tasksByMaterial = ref<Record<string, ParseTaskVO[]>>({})
const loading = ref(false)
const pageError = ref('')
const selectedFile = ref<File | null>(null)
const abortController = ref<AbortController | null>(null)
const abortRequested = ref(false)

const multipartState = reactive<MultipartState>({
  uploadId: '',
  materialId: '',
  fileName: '',
  fileSize: 0,
  partSize: 0,
  totalParts: 0,
  uploadedParts: 0,
  currentPart: 0,
  currentPartProgress: 0,
  percentage: 0,
  status: 'IDLE',
  error: '',
})

const isUploading = computed(() => ['INIT', 'UPLOADING', 'COMPLETING'].includes(multipartState.status))
const canResume = computed(() => multipartState.status === 'FAILED' && !!selectedFile.value && !!multipartState.uploadId)
const canAbort = computed(() => isUploading.value && !!multipartState.uploadId)

function resetMultipartState() {
  multipartState.uploadId = ''
  multipartState.materialId = ''
  multipartState.fileName = ''
  multipartState.fileSize = 0
  multipartState.partSize = 0
  multipartState.totalParts = 0
  multipartState.uploadedParts = 0
  multipartState.currentPart = 0
  multipartState.currentPartProgress = 0
  multipartState.percentage = 0
  multipartState.status = 'IDLE'
  multipartState.error = ''
  abortRequested.value = false
  abortController.value = null
}

function formatSize(value: string | number) {
  const size = Number(value)
  if (!Number.isFinite(size)) {
    return String(value)
  }
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  if (size < 1024 * 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(1)} MB`
  }
  return `${(size / 1024 / 1024 / 1024).toFixed(2)} GB`
}

function multipartStatusLabel() {
  switch (multipartState.status) {
    case 'INIT':
      return '正在初始化上传任务'
    case 'UPLOADING':
      return '正在上传分片'
    case 'COMPLETING':
      return '正在合并并生成资料'
    case 'FAILED':
      return '上传中断，可继续上传'
    case 'SUCCESS':
      return '上传完成，已进入解析队列'
    case 'ABORTED':
      return '上传已取消'
    default:
      return '等待选择文件'
  }
}

async function loadMaterials() {
  loading.value = true
  pageError.value = ''
  try {
    const allMaterials = await studyflowApi.listMaterials()
    materials.value = allMaterials.filter((item) => item.uploadStatus !== 'FAILED')
    await Promise.all(
      materials.value.slice(0, 8).map(async (item) => {
        tasksByMaterial.value[item.id] = await studyflowApi.listParseTasks(item.id)
      }),
    )
  } catch (error) {
    pageError.value = getDisplayError(error)
  } finally {
    loading.value = false
  }
}

async function refreshMaterialTask(materialId: string) {
  tasksByMaterial.value[materialId] = await studyflowApi.listParseTasks(materialId)
}

async function prepareMultipartSession(file: File) {
  const init = await studyflowApi.initMultipartUpload({
    fileName: file.name,
    fileSize: file.size,
    fileMd5: createFileFingerprint(file),
  })
  multipartState.uploadId = init.uploadId
  multipartState.materialId = init.materialId
  multipartState.fileName = file.name
  multipartState.fileSize = file.size
  multipartState.partSize = Number(init.partSize)
  multipartState.totalParts = Number(init.totalParts)
  multipartState.status = 'INIT'
}

async function uploadRemainingParts(file: File) {
  if (!multipartState.uploadId || !multipartState.partSize || !multipartState.totalParts) {
    await prepareMultipartSession(file)
  }

  multipartState.error = ''
  multipartState.status = 'UPLOADING'
  const uploaded = await studyflowApi.listUploadedParts(multipartState.uploadId)
  const uploadedPartNumbers = new Set(uploaded.uploadedParts.map((item) => item.partNumber))
  multipartState.uploadedParts = Number(uploaded.uploadedPartCount)
  multipartState.percentage = calculateUploadedPercentage(multipartState.uploadedParts, multipartState.totalParts)

  const plan = buildMultipartPlan(file.size, multipartState.partSize)
  for (const part of plan) {
    if (abortRequested.value) {
      return false
    }
    if (uploadedPartNumbers.has(part.partNumber)) {
      continue
    }

    multipartState.currentPart = part.partNumber
    multipartState.currentPartProgress = 0
    abortController.value = new AbortController()

    try {
      await studyflowApi.uploadMultipartPart(
        multipartState.uploadId,
        part.partNumber,
        file.slice(part.start, part.end),
        {
          signal: abortController.value.signal,
          onProgress: (progress) => {
            multipartState.currentPartProgress = progress
          },
        },
      )
      multipartState.uploadedParts += 1
      multipartState.percentage = calculateUploadedPercentage(multipartState.uploadedParts, multipartState.totalParts)
      multipartState.currentPartProgress = 100
    } catch (error) {
      if (abortRequested.value) {
        return false
      }
      multipartState.status = 'FAILED'
      multipartState.error = getDisplayError(error)
      throw error
    } finally {
      abortController.value = null
    }
  }

  if (abortRequested.value) {
    return false
  }

  try {
    multipartState.status = 'COMPLETING'
    const material = await studyflowApi.completeMultipartUpload(multipartState.uploadId)
    multipartState.status = 'SUCCESS'
    multipartState.percentage = 100
    materials.value = [material, ...materials.value.filter((item) => item.id !== material.id && item.uploadStatus !== 'FAILED')]
    await refreshMaterialTask(material.id)
    ElMessage.success('分片上传完成，资料已进入异步解析流程')
    return true
  } catch (error) {
    multipartState.status = 'FAILED'
    multipartState.error = getDisplayError(error)
    throw error
  }
}

async function startMultipartUpload(file: File) {
  selectedFile.value = file
  resetMultipartState()
  multipartState.fileName = file.name
  multipartState.fileSize = file.size
  return uploadRemainingParts(file)
}

async function handleUpload(options: UploadRequestOptions) {
  const file = options.file as File
  try {
    const completed = await startMultipartUpload(file)
    if (completed) {
      options.onSuccess?.({ uploadId: multipartState.uploadId })
    }
  } catch (error) {
    const message = getDisplayError(error)
    const uploadError = new Error(message) as Parameters<NonNullable<typeof options.onError>>[0]
    options.onError?.(uploadError)
  }
}

async function resumeMultipartUpload() {
  if (!selectedFile.value || !multipartState.uploadId) {
    return
  }
  abortRequested.value = false
  try {
    await uploadRemainingParts(selectedFile.value)
  } catch (error) {
    if (!abortRequested.value) {
      ElMessage.error(getDisplayError(error))
    }
  }
}

async function abortMultipartUpload() {
  if (!multipartState.uploadId) {
    return
  }
  abortRequested.value = true
  abortController.value?.abort()
  try {
    await studyflowApi.abortMultipartUpload(multipartState.uploadId)
    multipartState.status = 'ABORTED'
    multipartState.error = ''
    materials.value = materials.value.filter((item) => item.id !== multipartState.materialId && item.uploadStatus !== 'FAILED')
    ElMessage.info('已取消当前分片上传任务')
  } catch (error) {
    multipartState.status = 'FAILED'
    multipartState.error = getDisplayError(error)
    ElMessage.error(multipartState.error)
  } finally {
    abortController.value = null
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
        <p class="muted">支持 PDF、PPT/PPTX、Word/DOCX、TXT、Markdown、MP3、WAV、MP4 等资料上传与解析。</p>
      </div>
      <el-button :loading="loading" @click="loadMaterials">
        <el-icon><RefreshRight /></el-icon>
        刷新列表
      </el-button>
    </div>

    <div class="upload-layout">
      <el-upload
        drag
        action="#"
        :http-request="handleUpload"
        :show-file-list="false"
        :disabled="isUploading"
        accept=".pdf,.ppt,.pptx,.doc,.docx,.txt,.md,.markdown,.mp3,.wav,.mp4"
        class="upload-box"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">将文件拖到这里，或 <em>点击选择文件</em></div>
        <template #tip>
          <div class="el-upload__tip">当前上传区已切换为 MinIO 原生 multipart 上传，适合大视频和大课件资料。</div>
        </template>
      </el-upload>

      <div class="multipart-card">
        <div class="multipart-header">
          <div>
            <h3>大文件分片上传</h3>
            <p class="muted">后端返回 partSize 后，前端自动切片并逐片上传，支持继续上传和取消上传。</p>
          </div>
          <StatusBadge :status="multipartState.status === 'IDLE' ? 'WAITING' : multipartState.status" />
        </div>

        <div class="multipart-meta">
          <div class="meta-item">
            <span class="meta-label">文件名</span>
            <strong>{{ multipartState.fileName || '未选择文件' }}</strong>
          </div>
          <div class="meta-item">
            <span class="meta-label">文件大小</span>
            <strong>{{ multipartState.fileSize ? formatSize(multipartState.fileSize) : '--' }}</strong>
          </div>
          <div class="meta-item">
            <span class="meta-label">分片大小</span>
            <strong>{{ multipartState.partSize ? formatSize(multipartState.partSize) : '--' }}</strong>
          </div>
          <div class="meta-item">
            <span class="meta-label">分片总数</span>
            <strong>{{ multipartState.totalParts || '--' }}</strong>
          </div>
        </div>

        <div class="multipart-progress">
          <div class="progress-line">
            <span>{{ multipartStatusLabel() }}</span>
            <span>{{ multipartState.percentage }}%</span>
          </div>
          <el-progress :percentage="multipartState.percentage" :stroke-width="14" />
          <p class="muted small">
            已上传 {{ multipartState.uploadedParts }}/{{ multipartState.totalParts || 0 }} 个分片
            <template v-if="multipartState.currentPart">
              ，当前处理第 {{ multipartState.currentPart }} 片，单片进度 {{ multipartState.currentPartProgress }}%
            </template>
          </p>
          <p v-if="multipartState.error" class="error-text">{{ multipartState.error }}</p>
        </div>

        <div class="multipart-actions">
          <el-button :disabled="!canResume" @click="resumeMultipartUpload">
            <el-icon><RefreshRight /></el-icon>
            继续上传
          </el-button>
          <el-button type="danger" plain :disabled="!canAbort" @click="abortMultipartUpload">
            <el-icon><VideoPause /></el-icon>
            取消上传
          </el-button>
        </div>
      </div>
    </div>

    <LoadingBlock :loading="loading" :error="pageError" :empty="materials.length === 0" empty-text="还没有上传资料" @retry="loadMaterials">
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

.upload-layout {
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  gap: 18px;
  margin-bottom: 20px;
}

.upload-box {
  margin-bottom: 0;
}

.multipart-card {
  border: 1px solid rgba(17, 24, 39, 0.08);
  border-radius: 18px;
  padding: 18px;
  background: linear-gradient(145deg, rgba(250, 248, 240, 0.95), rgba(244, 247, 252, 0.95));
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.06);
}

.multipart-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.multipart-header h3 {
  margin: 0 0 6px;
  font-size: 18px;
}

.multipart-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 18px 0;
}

.meta-item {
  padding: 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.72);
}

.meta-label {
  display: block;
  margin-bottom: 6px;
  color: var(--sf-muted);
  font-size: 12px;
}

.multipart-progress {
  margin-bottom: 16px;
}

.progress-line {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  font-weight: 600;
}

.multipart-actions {
  display: flex;
  gap: 10px;
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

.error-text {
  margin-top: 8px;
  color: #c0392b;
  font-size: 13px;
}

@media (max-width: 1024px) {
  .upload-layout {
    grid-template-columns: 1fr;
  }
}
</style>
