import axios from 'axios'
import { useAuthStore } from '@/store/authStore'

export const apiClient = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
})

// Request interceptor — attach JWT
apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
    // For order-service which reads X-User-Id header (set by gateway, but we pass for dev)
    const user = useAuthStore.getState().user
    if (user) {
      config.headers['X-User-Id'] = user.keycloakId
      config.headers['X-User-Username'] = user.email
    }
  }
  return config
})

// Response interceptor — handle 401
apiClient.interceptors.response.use(
  (res) => res,
  (error) => {
    if (error.response?.status === 401) {
      useAuthStore.getState().logout()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

