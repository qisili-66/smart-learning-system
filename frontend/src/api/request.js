import axios from 'axios'
import { ElMessage } from 'element-plus'

const TOKEN_KEY = 'smart_learning_token'
const REFRESH_TOKEN_KEY = 'smart_learning_refresh_token'
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

export const getStoredToken = () => localStorage.getItem(TOKEN_KEY)

export const setStoredToken = (token) => {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

export const getStoredRefreshToken = () => localStorage.getItem(REFRESH_TOKEN_KEY)

export const setStoredRefreshToken = (token) => {
  if (token) {
    localStorage.setItem(REFRESH_TOKEN_KEY, token)
  } else {
    localStorage.removeItem(REFRESH_TOKEN_KEY)
  }
}

const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 120000
})

let refreshPromise = null

function redirectToLogin() {
  setStoredToken('')
  setStoredRefreshToken('')
  if (!window.location.pathname.includes('/login')) {
    const redirect = encodeURIComponent(window.location.pathname + window.location.search)
    window.location.assign(`/login?redirect=${redirect}`)
  }
}

async function refreshAccessToken() {
  const refreshToken = getStoredRefreshToken()
  if (!refreshToken) return null

  if (!refreshPromise) {
    refreshPromise = axios
      .post(`${API_BASE_URL}/auth/refresh`, { refreshToken }, { timeout: 30000 })
      .then((response) => {
        const body = response.data
        if (body?.code !== 200 || !body.data?.token) {
          throw new Error(body?.message || '登录状态已过期')
        }
        setStoredToken(body.data.token)
        setStoredRefreshToken(body.data.refreshToken || refreshToken)
        return body.data.token
      })
      .finally(() => {
        refreshPromise = null
      })
  }

  return refreshPromise
}

http.interceptors.request.use((config) => {
  const token = getStoredToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'code')) {
      if (body.code === 200) {
        return body.data
      }
      const message = body.message || '请求失败'
      if (body.code === 401) {
        redirectToLogin()
      } else {
        ElMessage.error(message)
      }
      return Promise.reject(new Error(message))
    }
    return body
  },
  async (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || '网络请求失败'

    if (status === 401) {
      const originalRequest = error.config
      const isRefreshRequest = String(originalRequest?.url || '').includes('/auth/refresh')
      if (originalRequest && !originalRequest._retry && !isRefreshRequest) {
        originalRequest._retry = true
        try {
          const token = await refreshAccessToken()
          if (token) {
            originalRequest.headers = originalRequest.headers || {}
            originalRequest.headers.Authorization = `Bearer ${token}`
            return http(originalRequest)
          }
        } catch {
          // Fall through and clear the expired session.
        }
      }
      redirectToLogin()
    } else if (status !== 403) {
      ElMessage.error(message)
    }

    return Promise.reject(new Error(message))
  }
)

export default http
