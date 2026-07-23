import { defineStore } from 'pinia'
import { authApi, userApi } from '@/api/student'
import { getStoredToken, setStoredRefreshToken, setStoredToken } from '@/api/request'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getStoredToken(),
    user: null,
    loading: false
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    isAdmin: (state) => state.user?.role === 2,
    displayName: (state) => state.user?.realName || state.user?.username || '同学'
  },
  actions: {
    setToken(token) {
      this.token = token || ''
      setStoredToken(this.token)
      if (!this.token) setStoredRefreshToken('')
    },
    async login(payload) {
      const data = await authApi.login(payload)
      this.setToken(data?.token)
      setStoredRefreshToken(data?.refreshToken)
      this.user = {
        userId: data?.userId,
        username: data?.username,
        realName: data?.realName,
        role: data?.role
      }
      await this.fetchUser().catch(() => undefined)
      return data
    },
    async register(payload) {
      return authApi.register(payload)
    },
    async fetchUser() {
      if (!this.token) return null
      this.loading = true
      try {
        this.user = await userApi.info()
        return this.user
      } finally {
        this.loading = false
      }
    },
    async updateInfo(payload) {
      this.user = await userApi.updateInfo(payload)
      return this.user
    },
    async changePassword(payload) {
      return userApi.changePassword(payload)
    },
    async logout() {
      if (this.token) {
        await authApi.logout().catch(() => undefined)
      }
      this.user = null
      this.setToken('')
    }
  }
})
