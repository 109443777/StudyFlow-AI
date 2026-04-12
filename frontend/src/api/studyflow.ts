import { request } from './request'
import type {
  AskQuestionRequest,
  GenerateReviewOutlineRequest,
  GenerateStudyPlanRequest,
  HealthCheckVO,
  LoginRequest,
  LoginVO,
  MaterialContentVO,
  MaterialSummaryVO,
  MaterialVO,
  ParseTaskVO,
  QaAnswerVO,
  QaMessageVO,
  QaSessionVO,
  RegisterRequest,
  StudyPlanDetailVO,
  StudyPlanHistoryVO,
  UserInfoVO,
} from '@/types/api'

export const studyflowApi = {
  health: () => request.get<HealthCheckVO>('/api/health'),
  register: (data: RegisterRequest) => request.post<UserInfoVO>('/api/auth/register', data),
  login: (data: LoginRequest) => request.post<LoginVO>('/api/auth/login', data),
  me: () => request.get<UserInfoVO>('/api/users/me'),
  uploadMaterial: (file: File, onProgress?: (percentage: number) => void) => {
    const form = new FormData()
    form.append('file', file)
    return request.post<MaterialVO, FormData>('/api/materials/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (event) => {
        if (!event.total || !onProgress) {
          return
        }
        onProgress(Math.round((event.loaded * 100) / event.total))
      },
    })
  },
  listMaterials: () => request.get<MaterialVO[]>('/api/materials/my'),
  getMaterial: (materialId: string) => request.get<MaterialVO>(`/api/materials/${materialId}`),
  listParseTasks: (materialId: string) => request.get<ParseTaskVO[]>('/api/parse-tasks', { params: { materialId } }),
  dispatchParse: (materialId: string) => request.post<ParseTaskVO>(`/api/parse-tasks/materials/${materialId}/dispatch`),
  getMaterialContent: (materialId: string) => request.get<MaterialContentVO>('/api/material-contents', { params: { materialId } }),
  getMaterialSummary: (materialId: string) => request.get<MaterialSummaryVO>('/api/material-summaries', { params: { materialId } }),
  generateMaterialSummary: (materialId: string) => request.post<MaterialSummaryVO>(`/api/material-summaries/${materialId}/generate`),
  listQaSessions: () => request.get<QaSessionVO[]>('/api/qa/sessions'),
  createQaSession: (materialIds: string[], sessionName?: string) =>
    request.post<QaSessionVO>('/api/qa/sessions', { materialIds, sessionName }),
  updateQaSessionMaterials: (sessionId: string, materialIds: string[]) =>
    request.put<QaSessionVO>(`/api/qa/sessions/${sessionId}/materials`, { materialIds }),
  askQuestion: (sessionId: string, data: AskQuestionRequest) => request.post<QaAnswerVO>(`/api/qa/sessions/${sessionId}/ask`, data),
  listQaMessages: (sessionId: string) => request.get<QaMessageVO[]>(`/api/qa/sessions/${sessionId}/messages`),
  generateReviewOutline: (data: GenerateReviewOutlineRequest) => request.post<StudyPlanDetailVO>('/api/study-plans/review-outline', data),
  generateExamPlan: (data: GenerateStudyPlanRequest) => request.post<StudyPlanDetailVO>('/api/study-plans/exam-plan', data),
  listStudyPlans: (materialId?: string) => request.get<StudyPlanHistoryVO[]>('/api/study-plans', { params: { materialId } }),
  getStudyPlan: (planId: string) => request.get<StudyPlanDetailVO>(`/api/study-plans/${planId}`),
}
