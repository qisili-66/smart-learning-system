import axios from 'axios'
import { ElMessage } from 'element-plus'

const TOKEN_KEY = 'smart_learning_token'

export const getStoredToken = () => localStorage.getItem(TOKEN_KEY)

export const setStoredToken = (token) => {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 120000
})

function redirectToLogin() {
  setStoredToken('')
  if (!window.location.pathname.includes('/login')) {
    const redirect = encodeURIComponent(window.location.pathname + window.location.search)
    window.location.assign(`/login?redirect=${redirect}`)
  }
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
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || '网络请求失败'

    if (status === 401) {
      redirectToLogin()
    } else if (status !== 403) {
      ElMessage.error(message)
    }

    return Promise.reject(new Error(message))
  }
)

export default http
