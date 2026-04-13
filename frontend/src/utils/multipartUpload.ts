export interface MultipartPartPlan {
  partNumber: number
  start: number
  end: number
}

export function buildMultipartPlan(fileSize: number, partSize: number): MultipartPartPlan[] {
  if (fileSize <= 0 || partSize <= 0) {
    return []
  }
  const plan: MultipartPartPlan[] = []
  let partNumber = 1
  for (let start = 0; start < fileSize; start += partSize) {
    plan.push({
      partNumber,
      start,
      end: Math.min(fileSize, start + partSize),
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
