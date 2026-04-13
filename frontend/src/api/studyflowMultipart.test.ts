import { describe, expect, it } from 'vitest'

import { studyflowApi } from '@/api/studyflow'

describe('studyflowApi multipart upload methods', () => {
  it('exposes multipart upload APIs', () => {
    expect(typeof studyflowApi.initMultipartUpload).toBe('function')
    expect(typeof studyflowApi.uploadMultipartPart).toBe('function')
    expect(typeof studyflowApi.listUploadedParts).toBe('function')
    expect(typeof studyflowApi.completeMultipartUpload).toBe('function')
    expect(typeof studyflowApi.abortMultipartUpload).toBe('function')
  })
})
