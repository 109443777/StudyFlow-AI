export interface MultipartPartPlan {
  partNumber: number
  start: number
  end: number
}

export function buildMultipartPlan(fileSize: number | string, partSize: number | string): MultipartPartPlan[] {
  const normalizedFileSize = Number(fileSize)
  const normalizedPartSize = Number(partSize)
  if (normalizedFileSize <= 0 || normalizedPartSize <= 0 || !Number.isFinite(normalizedFileSize) || !Number.isFinite(normalizedPartSize)) {
    return []
  }
  const plan: MultipartPartPlan[] = []
  let partNumber = 1
  for (let start = 0; start < normalizedFileSize; start += normalizedPartSize) {
    plan.push({
      partNumber,
      start,
      end: Math.min(normalizedFileSize, start + normalizedPartSize),
    })
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

export function createFileFingerprint(file: Pick<File, 'name' | 'size' | 'lastModified'>): string {
  const raw = `${file.name}|${file.size}|${file.lastModified}`
  const hex = Array.from(raw)
    .map((char) => char.charCodeAt(0).toString(16).padStart(2, '0'))
    .join('')
  return (hex + '0'.repeat(32)).slice(0, 32)
}

export async function createFileSha256(file: File): Promise<string> {
  const buffer = await file.arrayBuffer()
  const hashBuffer = await crypto.subtle.digest('SHA-256', buffer)
  return Array.from(new Uint8Array(hashBuffer))
    .map((item) => item.toString(16).padStart(2, '0'))
    .join('')
}
