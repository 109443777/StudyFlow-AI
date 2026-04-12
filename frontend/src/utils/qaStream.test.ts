import { describe, expect, it } from 'vitest'

import { parseQaStreamChunk } from './qaStream'

describe('qa stream parser', () => {
  it('parses complete SSE events and keeps incomplete remainder', () => {
    const input = [
      'event: context',
      'data: {"type":"context","references":[{"chunkId":"1"}]}',
      '',
      'event: chunk',
      'data: {"type":"chunk","content":"矩阵"}',
      '',
      'event: chunk',
      'data: {"type":"chunk","content":"是线性代数中的基础对象"}',
    ].join('\n')

    const parsed = parseQaStreamChunk(input)

    expect(parsed.events).toHaveLength(2)
    expect(parsed.events[0]).toMatchObject({ type: 'context' })
    expect(parsed.events[1]).toMatchObject({ type: 'chunk', content: '矩阵' })
    expect(parsed.remainder).toContain('event: chunk')
  })

  it('merges multi-line data payloads into a single JSON body', () => {
    const input = [
      'event: done',
      'data: {"type":"done",',
      'data: "answer":"矩阵可表示线性变换"}',
      '',
      '',
    ].join('\n')

    const parsed = parseQaStreamChunk(input)

    expect(parsed.events).toEqual([
      {
        type: 'done',
        answer: '矩阵可表示线性变换',
      },
    ])
    expect(parsed.remainder).toBe('')
  })
})
