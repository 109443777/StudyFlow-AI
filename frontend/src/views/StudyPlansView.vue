<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { studyflowApi } from '@/api/studyflow'
import type { MaterialVO, StudyPlanDetailVO, StudyPlanHistoryVO } from '@/types/api'
import { getDisplayError } from '@/utils/result'

const materials = ref<MaterialVO[]>([])
const plans = ref<StudyPlanHistoryVO[]>([])
const detail = ref<StudyPlanDetailVO>()
const loading = ref(false)
const generating = ref(false)

const form = reactive({
  materialId: '',
  planName: '期末复习计划',
  examDate: '',
})

async function load() {
  loading.value = true
  try {
    const [materialResult, planResult] = await Promise.all([studyflowApi.listMaterials(), studyflowApi.listStudyPlans()])
    materials.value = materialResult
    plans.value = planResult
    if (!form.materialId && materials.value.length) {
      form.materialId = materials.value[0].id
    }
  } catch (err) {
    ElMessage.error(getDisplayError(err))
  } finally {
    loading.value = false
  }
}

async function generateOutline() {
  generating.value = true
  try {
    detail.value = await studyflowApi.generateReviewOutline({ materialId: form.materialId, planName: `${form.planName}-复习提纲` })
    ElMessage.success('复习提纲生成成功')
    await load()
  } catch (err) {
    ElMessage.error(getDisplayError(err))
  } finally {
    generating.value = false
  }
}

async function generateExamPlan() {
  if (!form.examDate) {
    ElMessage.warning('请先选择考试日期')
    return
  }
  generating.value = true
  try {
    detail.value = await studyflowApi.generateExamPlan({
      materialId: form.materialId,
      planName: form.planName,
      examDate: form.examDate,
    })
    ElMessage.success('7 天复习计划生成成功')
    await load()
  } catch (err) {
    ElMessage.error(getDisplayError(err))
  } finally {
    generating.value = false
  }
}

async function openPlan(planId: string) {
  detail.value = await studyflowApi.getStudyPlan(planId)
}

onMounted(load)
</script>

<template>
  <section class="plans grid-2">
    <article class="page-card panel">
      <h2 class="section-title">生成学习辅助内容</h2>
      <el-form label-position="top">
        <el-form-item label="选择资料">
          <el-select v-model="form.materialId" filterable placeholder="请选择资料" class="full">
            <el-option v-for="material in materials" :key="material.id" :label="material.fileName" :value="material.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划名称">
          <el-input v-model="form.planName" />
        </el-form-item>
        <el-form-item label="考试日期">
          <el-date-picker v-model="form.examDate" value-format="YYYY-MM-DD" type="date" placeholder="选择考试日期" class="full" />
        </el-form-item>
        <el-space wrap>
          <el-button type="primary" :disabled="!form.materialId" :loading="generating" @click="generateOutline">生成复习提纲</el-button>
          <el-button :disabled="!form.materialId" :loading="generating" @click="generateExamPlan">生成 7 天复习计划</el-button>
          <el-button :loading="loading" @click="load">刷新历史</el-button>
        </el-space>
      </el-form>

      <el-divider />

      <h3>历史计划</h3>
      <el-table :data="plans" height="360">
        <el-table-column label="名称" prop="planName" min-width="160" />
        <el-table-column label="类型" prop="planType" width="130" />
        <el-table-column label="状态" prop="status" width="100" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="primary" @click="openPlan(row.id)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </article>

    <article class="page-card panel">
      <h2 class="section-title">计划详情</h2>
      <el-empty v-if="!detail" description="生成或选择一条学习计划后查看详情。" />
      <template v-else-if="detail.reviewOutline">
        <h3>{{ detail.planName }}</h3>
        <p class="text-block">{{ detail.reviewOutline.summary }}</p>
        <el-tag v-for="keyword in detail.reviewOutline.keywords" :key="keyword" class="keyword">{{ keyword }}</el-tag>
        <h4>复习清单</h4>
        <ol>
          <li v-for="item in detail.reviewOutline.reviewChecklist" :key="item">{{ item }}</li>
        </ol>
      </template>
      <template v-else-if="detail.examPlan">
        <h3>{{ detail.planName }} · 距离考试 {{ detail.examPlan.countdownDays }} 天</h3>
        <el-timeline>
          <el-timeline-item v-for="day in detail.examPlan.dailyPlans" :key="day.dayIndex" :timestamp="day.studyDate">
            <strong>第 {{ day.dayIndex }} 天：{{ day.theme }}</strong>
            <p>重点：{{ day.focusTopics.join('、') }}</p>
            <ul>
              <li v-for="task in day.tasks" :key="task">{{ task }}</li>
            </ul>
          </el-timeline-item>
        </el-timeline>
      </template>
    </article>
  </section>
</template>

<style scoped>
.panel {
  padding: 24px;
}

.full {
  width: 100%;
}

.keyword {
  margin: 0 8px 8px 0;
}
</style>
