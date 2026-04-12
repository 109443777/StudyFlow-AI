<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { studyflowApi } from '@/api/studyflow'
import StatusBadge from '@/components/StatusBadge.vue'
import type { MaterialVO, QaMessageVO, QaSessionVO } from '@/types/api'
import { buildQaAnswerLoadingSteps } from '@/utils/materialStatus'
import { getDisplayError } from '@/utils/result'

const route = useRoute()
const materials = ref<MaterialVO[]>([])
const sessions = ref<QaSessionVO[]>([])
const activeSession = ref<QaSessionVO>()
const selectedMaterialIds = ref<string[]>([])
const messages = ref<QaMessageVO[]>([])
const currentQuestion = ref('')
const sessionName = ref('')
const topK = ref(3)
const loading = ref(false)
const asking = ref(false)
const savingScope = ref(false)
const creatingSession = ref(false)
const deletingSessionId = ref('')
const loadingStepIndex = ref(0)
let loadingStepTimer: number | undefined

const answerLoadingSteps = buildQaAnswerLoadingSteps()

const availableMaterials = computed(() =>
  materials.value.filter(isMaterialReady),
)

const selectedMaterials = computed(() =>
  materials.value.filter((item) => selectedMaterialIds.value.includes(item.id)),
)

const activeSessionTitle = computed(() => activeSession.value?.sessionName || '还没有选择对话')

async function loadWorkspace() {
  loading.value = true
  try {
    const [materialResult, sessionResult] = await Promise.all([
      studyflowApi.listMaterials(),
      studyflowApi.listQaSessions(),
    ])
    materials.value = materialResult
    sessions.value = sessionResult

    const queryMaterialId = String(route.query.materialId || '')
    if (queryMaterialId) {
      selectedMaterialIds.value = [queryMaterialId]
    }

    if (sessions.value.length > 0) {
      await activateSession(sessions.value[0])
      return
    }

    if (!queryMaterialId && availableMaterials.value.length > 0) {
      selectedMaterialIds.value = [availableMaterials.value[0].id]
    }
  } catch (err) {
    ElMessage.error(getDisplayError(err))
  } finally {
    loading.value = false
  }
}

async function activateSession(qaSession: QaSessionVO) {
  activeSession.value = qaSession
  selectedMaterialIds.value = [...(qaSession.materialIds || [])]
  sessionName.value = qaSession.sessionName
  try {
    messages.value = await studyflowApi.listQaMessages(qaSession.id)
  } catch (err) {
    ElMessage.error(getDisplayError(err))
  }
}

async function createSession() {
  if (!ensureReadyMaterialSelection()) {
    return
  }
  creatingSession.value = true
  try {
    const name = sessionName.value.trim() || buildDefaultSessionName()
    const qaSession = await studyflowApi.createQaSession(selectedMaterialIds.value, name)
    sessions.value = [qaSession, ...sessions.value]
    await activateSession(qaSession)
    ElMessage.success('已创建新的持久化对话')
  } catch (err) {
    ElMessage.error(getDisplayError(err))
  } finally {
    creatingSession.value = false
  }
}

async function saveMaterialScope() {
  if (!activeSession.value) {
    await createSession()
    return
  }
  if (!ensureReadyMaterialSelection()) {
    return
  }
  savingScope.value = true
  try {
    const updatedSession = await studyflowApi.updateQaSessionMaterials(activeSession.value.id, selectedMaterialIds.value)
    activeSession.value = updatedSession
    sessions.value = sessions.value.map((item) => item.id === updatedSession.id ? updatedSession : item)
    ElMessage.success('当前对话的资料范围已更新')
  } catch (err) {
    ElMessage.error(getDisplayError(err))
  } finally {
    savingScope.value = false
  }
}

async function ask() {
  if (!currentQuestion.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  if (!activeSession.value) {
    await createSession()
  }
  if (!activeSession.value) {
    return
  }
  asking.value = true
  startAnswerLoading()
  const question = currentQuestion.value.trim()
  currentQuestion.value = ''
  messages.value.push({
    id: `local-question-${Date.now()}`,
    role: 'USER',
    content: question,
    referenceChunks: [],
    createTime: new Date().toISOString(),
  })
  try {
    const answer = await studyflowApi.askQuestion(activeSession.value.id, { question, topK: topK.value })
    messages.value.push({
      id: answer.answerMessageId,
      role: 'ASSISTANT',
      content: answer.answer,
      referenceChunks: answer.references,
      createTime: new Date().toISOString(),
    })
  } catch (err) {
    ElMessage.error(getDisplayError(err))
  } finally {
    stopAnswerLoading()
    asking.value = false
  }
}

async function deleteSession(session: QaSessionVO, event?: MouseEvent) {
  event?.stopPropagation()
  await ElMessageBox.confirm(`确定删除会话「${session.sessionName}」吗？会同时清空这段对话历史。`, '删除对话', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  deletingSessionId.value = session.id
  try {
    await studyflowApi.deleteQaSession(session.id)
    sessions.value = sessions.value.filter((item) => item.id !== session.id)
    if (activeSession.value?.id === session.id) {
      const nextSession = sessions.value[0]
      if (nextSession) {
        await activateSession(nextSession)
      } else {
        startNewSession()
      }
    }
    ElMessage.success('会话已删除')
  } catch (err) {
    ElMessage.error(getDisplayError(err))
  } finally {
    deletingSessionId.value = ''
  }
}

function startNewSession() {
  activeSession.value = undefined
  messages.value = []
  sessionName.value = buildDefaultSessionName()
  if (selectedMaterialIds.value.length === 0 && availableMaterials.value.length > 0) {
    selectedMaterialIds.value = [availableMaterials.value[0].id]
  }
}

function buildDefaultSessionName() {
  if (selectedMaterials.value.length === 0) {
    return '新的学习资料问答'
  }
  if (selectedMaterials.value.length === 1) {
    return `${selectedMaterials.value[0].fileName} 问答`
  }
  return `${selectedMaterials.value[0].fileName} 等 ${selectedMaterials.value.length} 份资料问答`
}

function isMaterialReady(material: MaterialVO) {
  return material.parseStatus === 'SUCCESS'
}

function ensureReadyMaterialSelection() {
  if (selectedMaterialIds.value.length === 0) {
    ElMessage.warning('请至少选择一份已完成解析的资料')
    return false
  }
  const notReadyMaterial = selectedMaterials.value.find((material) => !isMaterialReady(material))
  if (notReadyMaterial) {
    ElMessage.warning(`《${notReadyMaterial.fileName}》还没有完成解析和向量化，请稍后再选`)
    return false
  }
  return true
}

function startAnswerLoading() {
  loadingStepIndex.value = 0
  window.clearTimeout(loadingStepTimer)
  loadingStepTimer = window.setTimeout(() => {
    if (asking.value) {
      loadingStepIndex.value = 1
    }
  }, 900)
}

function stopAnswerLoading() {
  window.clearTimeout(loadingStepTimer)
  loadingStepIndex.value = 0
}

onMounted(loadWorkspace)
onUnmounted(stopAnswerLoading)
</script>

<template>
  <section class="qa-layout">
    <aside class="page-card qa-sidebar">
      <div class="sidebar-section">
        <div class="section-head">
          <h2 class="section-title">我的对话</h2>
          <el-button type="primary" link @click="startNewSession">新建</el-button>
        </div>
        <div class="session-list">
          <div
            v-for="item in sessions"
            :key="item.id"
            class="session-item"
            :class="{ active: activeSession?.id === item.id }"
            @click="activateSession(item)"
          >
            <div class="session-main">
              <strong>{{ item.sessionName }}</strong>
              <span>{{ item.materials?.length || item.materialIds?.length || 1 }} 份资料</span>
            </div>
            <el-button
              type="danger"
              link
              :loading="deletingSessionId === item.id"
              @click="deleteSession(item, $event)"
            >
              删除
            </el-button>
          </div>
          <el-empty v-if="sessions.length === 0" description="暂无对话，选择资料后提问即可创建" />
        </div>
      </div>

      <div class="sidebar-section">
        <div class="section-head">
          <h2 class="section-title">资料范围</h2>
          <el-button :loading="loading" link @click="loadWorkspace">刷新</el-button>
        </div>
        <el-input v-model="sessionName" placeholder="对话名称，例如：操作系统期末复习" />
        <el-checkbox-group v-model="selectedMaterialIds" class="material-checks">
          <el-checkbox
            v-for="material in materials"
            :key="material.id"
            :label="material.id"
            :disabled="!isMaterialReady(material)"
            class="material-check"
          >
            <span class="material-name">{{ material.fileName }}</span>
            <StatusBadge :status="material.parseStatus" />
          </el-checkbox>
        </el-checkbox-group>
        <div class="scope-actions">
          <el-button
            type="primary"
            :loading="activeSession ? savingScope : creatingSession"
            @click="saveMaterialScope"
          >
            {{ activeSession ? '保存资料范围' : '用所选资料创建对话' }}
          </el-button>
        </div>
        <p class="muted scope-tip">
          当前选中 {{ selectedMaterialIds.length }} 份资料。只有解析成功的资料可用于问答，后续提问只会在这些资料的向量片段中检索。
        </p>
      </div>
    </aside>

    <main class="page-card chat-panel">
      <div class="chat-header">
        <div>
          <h2 class="section-title">{{ activeSessionTitle }}</h2>
          <p class="muted">
            基于当前对话绑定的资料回答；对话历史会持久化保存，切换回来后可继续追问。
          </p>
        </div>
        <div class="topk-control">
          <span>召回数量</span>
          <el-input-number v-model="topK" :min="1" :max="10" size="small" />
        </div>
      </div>

      <div class="selected-materials" v-if="selectedMaterials.length">
        <el-tag v-for="material in selectedMaterials" :key="material.id" effect="plain">
          {{ material.fileName }}
        </el-tag>
      </div>

      <div class="messages">
        <el-empty
          v-if="messages.length === 0"
          description="选择左侧资料并提问，StudyFlow AI 会基于资料片段回答"
        />
        <article v-for="message in messages" :key="message.id" class="message" :class="message.role.toLowerCase()">
          <strong>{{ message.role === 'USER' ? '我' : 'StudyFlow AI' }}</strong>
          <p class="text-block">{{ message.content }}</p>
          <el-collapse v-if="message.referenceChunks?.length">
            <el-collapse-item title="查看引用片段">
              <div v-for="reference in message.referenceChunks" :key="reference.chunkId" class="reference">
                <strong>
                  {{ reference.fileName || '资料片段' }} / Chunk {{ reference.chunkIndex }} / score
                  {{ reference.score.toFixed(3) }}
                </strong>
                <p>{{ reference.chunkText }}</p>
              </div>
            </el-collapse-item>
          </el-collapse>
        </article>
      </div>

      <el-alert
        v-if="asking"
        :title="answerLoadingSteps[loadingStepIndex]"
        :description="loadingStepIndex === 0 ? '先从当前会话绑定的资料中召回最相关片段。' : '已拿到参考片段，正在组织上下文并请求大模型。'"
        type="info"
        :closable="false"
        show-icon
        class="asking-alert"
      />

      <div class="ask-box">
        <el-input
          v-model="currentQuestion"
          type="textarea"
          :rows="3"
          placeholder="例如：请综合当前选中的几份资料，总结考试最可能考到的重点"
          @keydown.ctrl.enter="ask"
        />
        <el-button type="primary" size="large" :loading="asking || creatingSession" @click="ask">
          发送问题
        </el-button>
      </div>
    </main>
  </section>
</template>

<style scoped>
.qa-layout {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 18px;
}

.qa-sidebar,
.chat-panel {
  padding: 24px;
}

.sidebar-section + .sidebar-section {
  margin-top: 24px;
  padding-top: 22px;
  border-top: 1px solid var(--sf-border);
}

.section-head,
.chat-header,
.ask-box {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
  max-height: 230px;
  overflow: auto;
}

.session-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 14px 16px;
  border: 1px solid var(--sf-border);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--sf-ink);
  cursor: pointer;
  transition: all 0.18s ease;
}

.session-main {
  min-width: 0;
}

.session-item strong,
.session-item span {
  display: block;
}

.session-item span {
  margin-top: 4px;
  color: var(--sf-muted);
  font-size: 13px;
}

.session-item.active {
  border-color: rgba(31, 111, 139, 0.5);
  background: rgba(31, 111, 139, 0.12);
  transform: translateY(-1px);
}

.material-checks {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 14px 0;
  max-height: 320px;
  overflow: auto;
}

.material-check {
  min-height: 42px;
  margin-right: 0;
  padding: 10px 12px;
  border: 1px solid var(--sf-border);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.68);
}

.material-name {
  display: inline-block;
  max-width: 210px;
  margin-right: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
}

.scope-actions .el-button {
  width: 100%;
}

.scope-tip {
  line-height: 1.7;
}

.topk-control {
  display: flex;
  gap: 8px;
  align-items: center;
  color: var(--sf-muted);
  white-space: nowrap;
}

.selected-materials {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 14px 0;
}

.messages {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 420px;
  max-height: 58vh;
  overflow: auto;
  padding: 12px 4px;
}

.asking-alert {
  margin-top: 8px;
}

.message {
  max-width: 86%;
  padding: 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.76);
}

.message.user {
  align-self: flex-end;
  background: rgba(31, 111, 139, 0.12);
}

.reference {
  padding: 12px;
  border-bottom: 1px solid var(--sf-border);
}

.reference p {
  margin-bottom: 0;
  line-height: 1.7;
}

.ask-box {
  margin-top: 14px;
}

.ask-box .el-button {
  min-width: 120px;
}

@media (max-width: 980px) {
  .qa-layout {
    grid-template-columns: 1fr;
  }

  .chat-header,
  .ask-box {
    flex-direction: column;
  }

  .ask-box .el-button {
    width: 100%;
  }
}
</style>
