import { describe, expect, it } from 'vitest'

import type { MaterialVO, ParseTaskVO } from '@/types/api'
import { buildMaterialStage, buildQaAnswerLoadingSteps } from '@/utils/materialStatus'

function material(overrides: Partial<MaterialVO> = {}): MaterialVO {
  return {
    id: '1',
    userId: '100',
    fileName: '课程录播.mp4',
    fileType: 'mp4',
    fileSize: '1024',
    objectKey: 'materials/100/demo.mp4',
    fileUrl: 'http://localhost/demo.mp4',
    materialType: 'VIDEO',
    parseStatus: 'PARSING',
    uploadStatus: 'SUCCESS',
    sourceType: 'USER_UPLOAD',
    createTime: '2026-04-12 10:00:00',
    updateTime: '2026-04-12 10:00:00',
    ...overrides,
  }
}

function task(overrides: Partial<ParseTaskVO> = {}): ParseTaskVO {
  return {
    id: '9',
    materialId: '1',
    userId: '100',
    taskType: 'VIDEO_TRANSCRIBE',
    status: 'RUNNING',
    retryCount: 0,
    createTime: '2026-04-12 10:00:00',
    updateTime: '2026-04-12 10:00:00',
    ...overrides,
  }
}

describe('materialStatus', () => {
  it('shows friendly text for running video transcription', () => {
    const stage = buildMaterialStage(material(), [task()])

    expect(stage.label).toBe('正在转写音视频')
    expect(stage.detail).toContain('音频抽取与语音识别')
    expect(stage.tone).toBe('warning')
  })

  it('shows index building stage for embedding task', () => {
    const stage = buildMaterialStage(
      material({ parseStatus: 'PARSING', materialType: 'DOCUMENT' }),
      [task({ taskType: 'EMBEDDING', status: 'RUNNING' })],
    )

    expect(stage.label).toBe('正在建立问答索引')
    expect(stage.detail).toContain('向量化')
  })

  it('shows success state when material parse is complete', () => {
    const stage = buildMaterialStage(material({ parseStatus: 'SUCCESS' }), [])

    expect(stage.label).toBe('解析完成')
    expect(stage.tone).toBe('success')
  })

  it('builds friendly qa loading steps', () => {
    expect(buildQaAnswerLoadingSteps()).toEqual([
      '正在检索相关资料片段',
      '正在组织上下文并生成回答',
    ])
  })
})
