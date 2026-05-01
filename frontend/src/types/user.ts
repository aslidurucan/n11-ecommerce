export interface SignupRequest {
  firstName: string
  lastName: string
  email: string
  password: string
}

export interface UpdateProfileRequest {
  firstName?: string
  lastName?: string
  email?: string
}

export interface UserProfileResponse {
  keycloakId: string
  firstName: string
  lastName: string
  email: string
  phone: string | null
  createdAt: string | null
}

export interface LoginRequest {
  email: string
  password: string
}

export interface TokenResponse {
  access_token: string
  refresh_token: string
  expires_in: number
  token_type: string
}
