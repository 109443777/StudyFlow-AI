<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'

import { studyflowApi } from '@/api/studyflow'
import StatusBadge from '@/components/StatusBadge.vue'
import type { MaterialVO, QaAnswerVO, QaMessageVO, QaSessionVO } from '@/types/api'
import { getDisplayError } from '@/utils/result'

const route = useRoute()
const materials = ref<MaterialVO[]>([])
const selectedMaterialId = ref('')
const session = ref<QaSessionVO>()
const messages = ref<QaMessageVO[]>([])
const currentQuestion = ref('')
const topK = ref(4)
const loading = ref(false)
const asking = ref(false)
const lastAnswer = ref<QaAnswerVO>()

const currentMaterial = computed(() => materials.value.find((item) => item.id === selectedMaterialId.value))

async function loadMaterials() {
  loading.value = true
  try {
    materials.value = await studyflowApi.listMaterials()
    selectedMaterialId.value = String(route.query.materialId || materials.value[0]?.id || '')
    if (selectedMaterialId.value) {
      await ensureSession()
    }
  } catch (err) {
    ElMessage.error(getDisplayError(err))
  } finally {
    loading.value = false
  }
}

async function ensureSession() {
  if (!selectedMaterialId.value) {
    return
  }
  session.value = await studyflowApi.createQaSession(selectedMaterialId.value, `${currentMaterial.value?.fileName || '资料'} 问答`)
  messages.value = []
  lastAnswer.value = undefined
}

async function ask() {
  if (!session.value || !currentQuestion.value.trim()) {
    ElMessage.warning('请先选择资料并输入问题')
    return
  }
  asking.value = true
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
    const answer = await studyflowApi.askQuestion(session.value.id, { question, topK: topK.value })
    lastAnswer.value = answer
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
    asking.value = false
  }
}

onMounted(loadMaterials)
</script>

<template>
  <section class="qa-layout">
    <aside class="page-card qa-panel">
      <h2 class="section-title">选择资料</h2>
      <el-select v-model="selectedMaterialId" filterable placeholder="请选择资料" class="full" @change="ensureSession">
        <el-option v-for="material in materials" :key="material.id" :label="material.fileName" :value="material.id">
          <span>{{ material.fileName }}</span>
          <StatusBadge :status="material.parseStatus" />
        </el-option>
      </el-select>
      <div v-if="currentMaterial" class="material-card">
        <strong>{{ currentMaterial.fileName }}</strong>
        <p class="muted">问答依赖该资料的 embedding 任务成功。如果提示“向量索引未就绪”，请回到资料详情查看解析任务。</p>
        <StatusBadge :status="currentMaterial.parseStatus" />
      </div>
      <el-slider v-model="topK" :min="1" :max="10" show-input>
        <template #default>召回数量</template>
      </el-slider>
    </aside>

    <main class="page-card chat-panel">
      <div class="chat-header">
        <div>
          <h2 class="section-title">基于资料问答</h2>
          <p class="muted">回答会尽量基于资料片段，并展示引用 chunk。</p>
        </div>
        <el-button :loading="loading" @click="loadMaterials">刷新资料</el-button>
      </div>

      <div class="messages">
        <el-empty v-if="messages.length === 0" description="先选择资料，然后问一个和课程内容相关的问题。" />
        <article v-for="message in messages" :key="message.id" class="message" :class="message.role.toLowerCase()">
          <strong>{{ message.role === 'USER' ? '我' : 'StudyFlow AI' }}</strong>
          <p class="text-block">{{ message.content }}</p>
          <el-collapse v-if="message.referenceChunks?.length">
            <el-collapse-item title="查看引用片段">
              <div v-for="reference in message.referenceChunks" :key="reference.chunkId" class="reference">
                <strong>Chunk {{ reference.chunkIndex }} · score {{ reference.score.toFixed(3) }}</strong>
                <p>{{ reference.chunkText }}</p>
              </div>
            </el-collapse-item>
          </el-collapse>
        </article>
      </div>

      <div class="ask-box">
        <el-input
          v-model="currentQuestion"
          type="textarea"
          :rows="3"
          placeholder="例如：请总结这篇论文的核心贡献，或者解释 KG 和 LLM 在资料中的关系"
          @keydown.ctrl.enter="ask"
        />
        <el-button type="primary" size="large" :loading="asking" @click="ask">发送问题</el-button>
      </div>
    </main>
  </section>
</template>

<style scoped>
.qa-layout {
  display: grid;
  grid-template-columns: 330px minmax(0, 1fr);
  gap: 18px;
}

.qa-panel,
.chat-panel {
  padding: 24px;
}

.full {
  width: 100%;
}

.material-card {
  margin: 18px 0;
  padding: 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.68);
}

.material-card p {
  line-height: 1.7;
}

.chat-header,
.ask-box {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
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
