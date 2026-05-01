import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { UserProfileResponse } from '@/types/user'

interface AuthState {
  token: string | null
  refreshToken: string | null
  user: UserProfileResponse | null
  isAuthenticated: boolean
  setToken: (token: string, refreshToken: string) => void
  setAuth: (token: string, refreshToken: string, user: UserProfileResponse) => void
  setUser: (user: UserProfileResponse) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      refreshToken: null,
      user: null,
      isAuthenticated: false,

      // Sadece token'ı yaz (getMe() öncesi kullanılır)
      setToken: (token, refreshToken) =>
        set({ token, refreshToken }),

      setAuth: (token, refreshToken, user) =>
        set({ token, refreshToken, user, isAuthenticated: true }),

      setUser: (user) => set({ user }),

      logout: () =>
        set({ token: null, refreshToken: null, user: null, isAuthenticated: false }),
    }),
    {
      name: 'n11-auth',
      partialize: (state) => ({
        token: state.token,
        refreshToken: state.refreshToken,
        user: state.user,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
)
