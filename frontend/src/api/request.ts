import axios, { AxiosError, type AxiosRequestConfig } from 'axios'

import type { Result } from '@/types/api'
import { StudyFlowError, unwrapResult } from '@/utils/result'

const TOKEN_KEY = 'studyflow_token'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 90_000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<Result<unknown>>) => {
    const payload = error.response?.data
    if (payload && typeof payload.code === 'number') {
      throw new StudyFlowError(payload.code, payload.message, payload.message)
    }
    if (error.code === 'ECONNABORTED') {
      throw new StudyFlowError(50001, '请求超时，请稍后再试。')
    }
    throw new StudyFlowError(50000, '网络异常或后端服务不可用，请检查服务是否启动。', error.message)
  },
)

export const request = {
  async get<T>(url: string, config?: AxiosRequestConfig) {
    const response = await http.get<Result<T>>(url, config)
    return unwrapResult(response.data)
  },
  async post<T, D = unknown>(url: string, data?: D, config?: AxiosRequestConfig<D>) {
    const response = await http.post<Result<T>>(url, data, config)
    return unwrapResult(response.data)
  },
  async put<T, D = unknown>(url: string, data?: D, config?: AxiosRequestConfig<D>) {
    const response = await http.put<Result<T>>(url, data, config)
    return unwrapResult(response.data)
  },
}

export function setAuthToken(token: string | null) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

export function getAuthToken() {
  return localStorage.getItem(TOKEN_KEY)
}
