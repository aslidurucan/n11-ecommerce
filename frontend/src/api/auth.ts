import axios from 'axios'
import type { TokenResponse } from '@/types/user'

const REALM = 'ecommerce'
const CLIENT_ID = 'new-user-service'

// Keycloak istekleri Vite proxy üzerinden gider (/keycloak → localhost:8090)
// Böylece browser aynı origin'e istek atıyor → CORS yok
const keycloakProxy = axios.create({
  baseURL: '/keycloak',
  timeout: 10000,
})

export const authApi = {
  login: async (email: string, password: string): Promise<TokenResponse> => {
    const params = new URLSearchParams({
      grant_type: 'password',
      client_id: CLIENT_ID,
      username: email,
      password,
    })
    const res = await keycloakProxy.post<TokenResponse>(
      `/realms/${REALM}/protocol/openid-connect/token`,
      params,
      { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
    )
    return res.data
  },

  logout: async (refreshToken: string): Promise<void> => {
    const params = new URLSearchParams({
      client_id: CLIENT_ID,
      refresh_token: refreshToken,
    })
    await keycloakProxy.post(
      `/realms/${REALM}/protocol/openid-connect/logout`,
      params,
      { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
    )
  },
}
