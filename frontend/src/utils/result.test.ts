import { describe, expect, it } from 'vitest'

import { StudyFlowError, getDisplayError, mapResultMessage, unwrapResult } from './result'

describe('result utilities', () => {
  it('unwraps successful backend Result payloads', () => {
    expect(unwrapResult({ code: 0, message: 'success', data: { id: '1' } })).toEqual({ id: '1' })
  })

  it('maps vector-not-ready errors to a student-friendly Chinese message', () => {
    expect(() => unwrapResult({ code: 40006, message: 'vector index is not ready', data: null }))
      .toThrow('向量索引还没有生成完成，请稍后再试。')
  })

  it('preserves unknown backend messages as fallback display text', () => {
    expect(mapResultMessage(49999, 'custom backend failure')).toBe('custom backend failure')
  })

  it('formats StudyFlowError instances without losing the user-facing message', () => {
    expect(getDisplayError(new StudyFlowError(50001, 'AI 服务繁忙或超时，请稍后再试。'))).toBe(
      'AI 服务繁忙或超时，请稍后再试。',
    )
  })
})
