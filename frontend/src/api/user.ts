import { apiClient } from './axios'
import type { SignupRequest, UpdateProfileRequest, UserProfileResponse } from '@/types/user'

export const userApi = {
  signup: async (req: SignupRequest): Promise<UserProfileResponse> => {
    const res = await apiClient.post<UserProfileResponse>('/users/signup', req)
    return res.data
  },

  getMe: async (): Promise<UserProfileResponse> => {
    const res = await apiClient.get<UserProfileResponse>('/users/me')
    return res.data
  },

  updateMe: async (req: UpdateProfileRequest): Promise<UserProfileResponse> => {
    const res = await apiClient.put<UserProfileResponse>('/users/me', req)
    return res.data
  },
}
