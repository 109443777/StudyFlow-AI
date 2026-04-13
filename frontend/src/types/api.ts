export type Id = string

export interface Result<T> {
  code: number
  message: string
  data: T
}

export interface UserInfoVO {
  id: Id
  username: string
  nickname: string
  avatar?: string
  status: number
}

export interface LoginVO {
  accessToken: string
  tokenType: string
  expiresIn: number
  userInfo: UserInfoVO
}

export interface HealthCheckVO {
  application: string
  status: string
  aiProvider: string
  embeddingProvider: string
  transcriptionProvider: string
  chatModel: string
  embeddingModel: string
  timestamp: string
}

export interface MaterialVO {
  id: Id
  userId: Id
  fileName: string
  fileType: string
  fileSize: string
  objectKey: string
  fileUrl: string
  materialType: string
  parseStatus: string
  uploadStatus: string
  sourceType: string
  createTime: string
  updateTime: string
}

export interface ParseTaskVO {
  id: Id
  materialId: Id
  userId: Id
  taskType: string
  status: string
  retryCount: number
  failReason?: string
  startTime?: string
  endTime?: string
  createTime: string
  updateTime: string
}

export interface MultipartInitRequest {
  fileName: string
  fileSize: number
  fileMd5: string
}

export interface MultipartInitVO {
  uploadId: Id
  materialId: Id
  partSize: number | string
  totalParts: number
  status: string
}

export interface MultipartUploadPartVO {
  uploadId: Id
  partNumber: number
  etag: string
  uploadedPartCount: number
  alreadyUploaded: boolean
  completed: boolean
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

export interface MaterialContentVO {
  id: Id
  materialId: Id
  contentType: string
  rawText: string
  cleanedText: string
  chapterInfo: string[]
  createTime: string
  updateTime: string
}

export interface ChapterHighlightVO {
  chapterTitle: string
  highlights: string[]
}

export interface MaterialSummaryVO {
  id: Id
  materialId: Id
  summaryText: string
  keywords: string[]
  keyPoints: string[]
  chapterHighlights: ChapterHighlightVO[]
  reviewOutline: string[]
  createTime: string
  updateTime: string
}

export interface QaSessionVO {
  id: Id
  materialId: Id
  materialIds: Id[]
  materials: QaSessionMaterialVO[]
  sessionName: string
  createTime: string
  updateTime: string
}

export interface QaSessionMaterialVO {
  id: Id
  fileName: string
  materialType: string
  parseStatus: string
}

export interface ChunkReferenceVO {
  chunkId: Id
  materialId?: Id
  fileName?: string
  chunkIndex: number
  score: number
  chunkText: string
}

export interface QaAnswerVO {
  sessionId: Id
  questionMessageId: Id
  answerMessageId: Id
  answer: string
  references: ChunkReferenceVO[]
}

export interface QaStreamEventVO {
  type: 'context' | 'chunk' | 'done' | 'error'
  sessionId?: Id
  questionMessageId?: Id
  answerMessageId?: Id
  content?: string
  answer?: string
  message?: string
  references?: ChunkReferenceVO[]
}

export interface QaMessageVO {
  id: Id
  role: string
  content: string
  referenceChunks: ChunkReferenceVO[]
  createTime: string
}

export interface ChapterReviewFocusVO {
  chapterTitle: string
  focusPoints: string[]
}

export interface ReviewOutlineContentVO {
  summary: string
  keywords: string[]
  keyPoints: string[]
  chapterFocuses: ChapterReviewFocusVO[]
  reviewChecklist: string[]
}

export interface DailyStudyPlanVO {
  dayIndex: number
  studyDate: string
  theme: string
  focusTopics: string[]
  tasks: string[]
}

export interface ExamStudyPlanContentVO {
  examDate: string
  countdownDays: number
  dailyPlans: DailyStudyPlanVO[]
  finalTips: string[]
}

export interface StudyPlanDetailVO {
  id: Id
  materialId: Id
  planType: string
  planName: string
  examDate?: string
  status: string
  reviewOutline?: ReviewOutlineContentVO
  examPlan?: ExamStudyPlanContentVO
  createTime: string
  updateTime: string
}

export interface StudyPlanHistoryVO {
  id: Id
  materialId: Id
  planType: string
  planName: string
  examDate?: string
  status: string
  createTime: string
  updateTime: string
}

export interface RegisterRequest {
  username: string
  password: string
  nickname: string
  avatar?: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface CreateQaSessionRequest {
  materialId?: Id
  materialIds?: Id[]
  sessionName?: string
}

export interface AskQuestionRequest {
  question: string
  topK?: number
}

export interface GenerateReviewOutlineRequest {
  materialId: Id
  planName?: string
}

export interface GenerateStudyPlanRequest {
  materialId: Id
  planName?: string
  examDate: string
}
