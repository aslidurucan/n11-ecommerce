import { apiClient } from './axios'
import type { CartResponse, AddItemRequest, UpdateQuantityRequest } from '@/types/cart'

export const cartApi = {
  getCart: async (): Promise<CartResponse> => {
    const res = await apiClient.get<CartResponse>('/cart')
    return res.data
  },

  addItem: async (req: AddItemRequest): Promise<CartResponse> => {
    const res = await apiClient.post<CartResponse>('/cart/items', req)
    return res.data
  },

  updateQuantity: async (productId: number, req: UpdateQuantityRequest): Promise<CartResponse> => {
    const res = await apiClient.put<CartResponse>(`/cart/items/${productId}`, req)
    return res.data
  },

  removeItem: async (productId: number): Promise<void> => {
    await apiClient.delete(`/cart/items/${productId}`)
  },

  clearCart: async (): Promise<void> => {
    await apiClient.delete('/cart')
  },
}
