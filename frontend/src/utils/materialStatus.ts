import type { MaterialVO, ParseTaskVO } from '@/types/api'

type StageTone = 'success' | 'warning' | 'error' | 'info'

export interface MaterialStage {
  label: string
  detail: string
  tone: StageTone
}

export function buildMaterialStage(material: MaterialVO, tasks: ParseTaskVO[] = []): MaterialStage {
  const latestTask = tasks[0]

  if (material.uploadStatus === 'FAILED') {
    return {
      label: '上传未完成',
      detail: '该资料上传已取消或失败，不会进入解析队列。请重新上传完整文件。',
      tone: 'error',
    }
  }

  if (material.parseStatus === 'SUCCESS') {
    return {
      label: '解析完成',
      detail: '文本、摘要和问答索引已经准备好，可以直接提问。',
      tone: 'success',
    }
  }

  if (material.parseStatus === 'FAILED' || latestTask?.status === 'FAILED') {
    return {
      label: '解析失败',
      detail: latestTask?.failReason || '异步处理未成功完成，可以稍后重新投递任务。',
      tone: 'error',
    }
  }

  if (!latestTask) {
    return {
      label: '等待解析',
      detail: '文件已上传完成，系统正在准备异步解析任务。',
      tone: 'info',
    }
  }

  if (latestTask.status === 'QUEUED') {
    return {
      label: '排队处理中',
      detail: describeTask(latestTask.taskType, '任务已进入队列，正在等待工作线程处理。'),
      tone: 'info',
    }
  }

  if (latestTask.status === 'RUNNING') {
    return {
      label: runningLabel(latestTask.taskType),
      detail: describeTask(latestTask.taskType, '系统正在处理当前资料，请稍候刷新。'),
      tone: 'warning',
    }
  }

  return {
    label: '处理中',
    detail: describeTask(latestTask.taskType, '系统正在继续处理当前资料。'),
    tone: 'warning',
  }
}

export function buildQaAnswerLoadingSteps() {
  return [
    '正在检索相关资料片段',
    '正在组织上下文并生成回答',
  ]
}

function runningLabel(taskType?: string) {
  if (taskType === 'VIDEO_TRANSCRIBE' || taskType === 'AUDIO_TRANSCRIBE') {
    return '正在转写音视频'
  }
  if (taskType === 'TEXT_PARSE') {
    return '正在解析文档文本'
  }
  if (taskType === 'AI_SUMMARY') {
    return '正在生成 AI 摘要'
  }
  if (taskType === 'EMBEDDING') {
    return '正在建立问答索引'
  }
  return '正在处理中'
}

function describeTask(taskType?: string, fallback = '系统正在处理当前资料。') {
  if (taskType === 'VIDEO_TRANSCRIBE') {
    return '系统正在完成音频抽取与语音识别，大视频通常会更久一些。'
  }
  if (taskType === 'AUDIO_TRANSCRIBE') {
    return '系统正在进行语音识别，转写完成后会自动生成摘要和索引。'
  }
  if (taskType === 'TEXT_PARSE') {
    return '系统正在抽取文档正文与章节信息，完成后会进入摘要和索引阶段。'
  }
  if (taskType === 'AI_SUMMARY') {
    return '系统正在生成摘要、关键词和知识点，请再稍等一下。'
  }
  if (taskType === 'EMBEDDING') {
    return '系统正在向量化资料内容，为后续问答建立检索索引。'
  }
  return fallback
}
