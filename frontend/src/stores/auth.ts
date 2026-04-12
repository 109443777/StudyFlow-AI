import { defineStore } from 'pinia'

import { getAuthToken, setAuthToken } from '@/api/request'
import { studyflowApi } from '@/api/studyflow'
import type { LoginRequest, RegisterRequest, UserInfoVO } from '@/types/api'

const USER_KEY = 'studyflow_user'

function readStoredUser(): UserInfoVO | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as UserInfoVO
  } catch {
    localStorage.removeItem(USER_KEY)
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getAuthToken(),
    user: readStoredUser(),
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
  },
  actions: {
    async register(payload: RegisterRequest) {
      await studyflowApi.register(payload)
      await this.login({ username: payload.username, password: payload.password })
    },
    async login(payload: LoginRequest) {
      const login = await studyflowApi.login(payload)
      this.token = login.accessToken
      this.user = login.userInfo
      setAuthToken(login.accessToken)
      localStorage.setItem(USER_KEY, JSON.stringify(login.userInfo))
    },
    async refreshUser() {
      if (!this.token) {
        return
      }
      this.user = await studyflowApi.me()
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
    },
    logout() {
      this.token = null
      this.user = null
      setAuthToken(null)
      localStorage.removeItem(USER_KEY)
    },
  },
})
