import { describe, expect, it } from 'vitest'

import { buildMultipartPlan, calculateUploadedPercentage, createFileFingerprint } from '@/utils/multipartUpload'

describe('multipartUpload utilities', () => {
  it('splits file size into 1-based multipart ranges', () => {
    expect(buildMultipartPlan(25, 8)).toEqual([
      { partNumber: 1, start: 0, end: 8 },
      { partNumber: 2, start: 8, end: 16 },
      { partNumber: 3, start: 16, end: 24 },
      { partNumber: 4, start: 24, end: 25 },
    ])
  })

  it('normalizes string part size returned by backend Long serialization', () => {
    expect(buildMultipartPlan(74_137_795, '8388608')).toHaveLength(9)
  })

  it('calculates uploaded percentage from completed parts', () => {
    expect(calculateUploadedPercentage(2, 4)).toBe(50)
    expect(calculateUploadedPercentage(5, 4)).toBe(100)
    expect(calculateUploadedPercentage(0, 0)).toBe(0)
  })

  it('builds a stable 32-character upload fingerprint', () => {
    expect(createFileFingerprint({ name: 'lesson.mp4', size: 1024, lastModified: 123456 })).toBe(
      '6c6573736f6e2e6d70347c313032347c',
    )
  })
})
