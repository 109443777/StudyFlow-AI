# Frontend Multipart Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the frontend materials page to use the backend's native MinIO multipart upload APIs with progress display, resume, and abort support.

**Architecture:** Keep multipart upload on the existing materials page. Add typed API methods for `init/part/parts/complete/abort`, move slice/retry/progress orchestration into a focused utility, and update `MaterialsView.vue` to drive the multipart flow with clear Chinese status text. Use targeted Vitest coverage for slice math and progress aggregation, then verify end-to-end with frontend build/tests and a live backend smoke flow.

**Tech Stack:** Vue 3, TypeScript, Element Plus, Axios, Vitest

---

## File Map

### API / types
- Modify: `frontend/src/api/studyflow.ts`
- Modify: `frontend/src/types/api.ts`

### Multipart orchestration
- Add: `frontend/src/utils/multipartUpload.ts`
- Add: `frontend/src/utils/multipartUpload.test.ts`

### View
- Modify: `frontend/src/views/MaterialsView.vue`

---

### Task 1: Add multipart API contracts and typed client methods

**Files:**
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/api/studyflow.ts`

- [ ] **Step 1: Write the failing type/client test**

```ts
import { describe, expect, it } from 'vitest'
import { studyflowApi } from '@/api/studyflow'

describe('studyflowApi multipart surface', () => {
  it('exposes multipart upload methods', () => {
    expect(typeof studyflowApi.initMultipartUpload).toBe('function')
    expect(typeof studyflowApi.uploadMultipartPart).toBe('function')
    expect(typeof studyflowApi.listUploadedParts).toBe('function')
    expect(typeof studyflowApi.completeMultipartUpload).toBe('function')
    expect(typeof studyflowApi.abortMultipartUpload).toBe('function')
  })
})
```

- [ ] **Step 2: Run the frontend test to verify it fails**

Run: `npm test -- multipartUpload`

Expected: FAIL because multipart client methods do not exist yet.

- [ ] **Step 3: Add multipart request/response types and API methods**

```ts
export interface MultipartInitRequest {
  fileName: string
  fileSize: number
  fileMd5: string
}

export interface MultipartInitVO {
  uploadId: Id
  materialId: Id
  partSize: number
  totalParts: number
  status: string
}

export interface UploadedPartVO {
  partNumber: number
  etag: string
}

export interface MultipartPartsVO {
  uploadId: Id
  totalParts: number
  uploadedPartCount: number
  uploadedParts: UploadedPartVO[]
  status: string
  completed: boolean
}
```

```ts
initMultipartUpload: (data: MultipartInitRequest) =>
  request.post<MultipartInitVO>('/api/material-uploads/init', data),
uploadMultipartPart: (uploadId: string, partNumber: number, part: Blob) => {
  const form = new FormData()
  form.append('uploadId', uploadId)
  form.append('partNumber', String(partNumber))
  form.append('part', new File([part], `part-${partNumber}.bin`, { type: 'application/octet-stream' }))
  return request.post('/api/material-uploads/part', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
},
listUploadedParts: (uploadId: string) => request.get<MultipartPartsVO>(`/api/material-uploads/${uploadId}/parts`),
completeMultipartUpload: (uploadId: string) => request.post<MaterialVO>('/api/material-uploads/complete', { uploadId }),
abortMultipartUpload: (uploadId: string) => request.post<void>('/api/material-uploads/abort', { uploadId }),
```

- [ ] **Step 4: Run the targeted frontend test again**

Run: `npm test -- multipartUpload`

Expected: PASS

---

### Task 2: Add multipart slicing and progress utility

**Files:**
- Add: `frontend/src/utils/multipartUpload.ts`
- Add: `frontend/src/utils/multipartUpload.test.ts`

- [ ] **Step 1: Write the failing utility test**

```ts
import { describe, expect, it } from 'vitest'
import { buildMultipartPlan, calculateUploadedPercentage } from '@/utils/multipartUpload'

describe('multipartUpload utility', () => {
  it('splits file size into 1-based part ranges', () => {
    const plan = buildMultipartPlan(25, 8)
    expect(plan).toEqual([
      { partNumber: 1, start: 0, end: 8 },
      { partNumber: 2, start: 8, end: 16 },
      { partNumber: 3, start: 16, end: 24 },
      { partNumber: 4, start: 24, end: 25 },
    ])
  })

  it('calculates uploaded percentage from completed parts', () => {
    expect(calculateUploadedPercentage(2, 4)).toBe(50)
  })
})
```

- [ ] **Step 2: Run the targeted test to verify it fails**

Run: `npm test -- multipartUpload`

Expected: FAIL because the utility does not exist yet.

- [ ] **Step 3: Implement the minimal utility**

```ts
export interface MultipartPartPlan {
  partNumber: number
  start: number
  end: number
}

export function buildMultipartPlan(fileSize: number, partSize: number): MultipartPartPlan[] {
  const plan: MultipartPartPlan[] = []
  let partNumber = 1
  for (let start = 0; start < fileSize; start += partSize) {
    plan.push({ partNumber, start, end: Math.min(fileSize, start + partSize) })
    partNumber += 1
  }
  return plan
}

export function calculateUploadedPercentage(uploadedCount: number, totalParts: number): number {
  if (totalParts <= 0) {
    return 0
  }
  return Math.min(100, Math.round((uploadedCount / totalParts) * 100))
}
```

- [ ] **Step 4: Run the targeted test again**

Run: `npm test -- multipartUpload`

Expected: PASS

---

### Task 3: Replace the current materials upload box with multipart orchestration

**Files:**
- Modify: `frontend/src/views/MaterialsView.vue`
- Modify: `frontend/src/api/studyflow.ts`
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/utils/multipartUpload.ts`

- [ ] **Step 1: Write the failing view test**

```ts
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MaterialsView from '@/views/MaterialsView.vue'

describe('MaterialsView multipart uploader', () => {
  it('shows multipart status card placeholder', () => {
    const wrapper = mount(MaterialsView)
    expect(wrapper.text()).toContain('大文件分片上传')
  })
})
```

- [ ] **Step 2: Run the targeted frontend test to verify it fails**

Run: `npm test -- MaterialsView`

Expected: FAIL because the multipart status card is not rendered yet.

- [ ] **Step 3: Implement multipart upload orchestration in the materials page**

```ts
const multipartState = reactive({
  uploadId: '',
  materialId: '',
  fileName: '',
  fileSize: 0,
  partSize: 0,
  totalParts: 0,
  uploadedParts: 0,
  percentage: 0,
  currentPart: 0,
  status: 'IDLE',
  error: '',
})
```

```ts
async function startMultipartUpload(file: File) {
  const md5 = `${file.name}-${file.size}-${file.lastModified}`
  const init = await studyflowApi.initMultipartUpload({
    fileName: file.name,
    fileSize: file.size,
    fileMd5: md5,
  })
  const plan = buildMultipartPlan(file.size, init.partSize)
  const uploaded = await studyflowApi.listUploadedParts(init.uploadId)
  const uploadedPartNumbers = new Set(uploaded.uploadedParts.map((item) => item.partNumber))

  for (const part of plan) {
    if (uploadedPartNumbers.has(part.partNumber)) {
      continue
    }
    multipartState.currentPart = part.partNumber
    const blob = file.slice(part.start, part.end)
    await studyflowApi.uploadMultipartPart(init.uploadId, part.partNumber, blob)
    multipartState.uploadedParts += 1
    multipartState.percentage = calculateUploadedPercentage(multipartState.uploadedParts, init.totalParts)
  }

  const material = await studyflowApi.completeMultipartUpload(init.uploadId)
  materials.value = [material, ...materials.value]
}
```

```vue
<div class="multipart-card">
  <h3>大文件分片上传</h3>
  <p class="muted">按后端返回的 partSize 自动切片，支持继续上传和取消上传。</p>
</div>
```

- [ ] **Step 4: Run the targeted frontend test again**

Run: `npm test -- MaterialsView`

Expected: PASS

---

### Task 4: Add resume/cancel interactions and final verification

**Files:**
- Modify: `frontend/src/views/MaterialsView.vue`
- Modify: `frontend/src/utils/multipartUpload.ts`

- [ ] **Step 1: Extend the failing test for cancel/resume text states**

```ts
it('shows resume and cancel actions while multipart upload is active', async () => {
  const wrapper = mount(MaterialsView)
  expect(wrapper.text()).toContain('继续上传')
  expect(wrapper.text()).toContain('取消上传')
})
```

- [ ] **Step 2: Run the targeted test to verify it fails**

Run: `npm test -- MaterialsView`

Expected: FAIL because action buttons are not rendered yet.

- [ ] **Step 3: Implement cancel/resume UI and state transitions**

```ts
async function resumeMultipartUpload() {
  if (!selectedFile.value || !multipartState.uploadId) {
    return
  }
  await uploadRemainingParts(selectedFile.value, multipartState.uploadId)
}

async function abortMultipartUpload() {
  if (!multipartState.uploadId) {
    return
  }
  await studyflowApi.abortMultipartUpload(multipartState.uploadId)
  multipartState.status = 'ABORTED'
}
```

```vue
<div class="multipart-actions" v-if="multipartState.status !== 'IDLE'">
  <el-button :disabled="multipartState.status !== 'FAILED'" @click="resumeMultipartUpload">继续上传</el-button>
  <el-button :disabled="!canAbort" @click="abortMultipartUpload">取消上传</el-button>
</div>
```

- [ ] **Step 4: Run full frontend verification**

Run:
- `npm test`
- `npm run build`

Expected:
- All Vitest cases pass
- Build succeeds

