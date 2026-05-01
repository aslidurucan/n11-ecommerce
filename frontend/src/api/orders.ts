import { apiClient } from './axios'
import type { CreateOrderRequest, OrderResponse } from '@/types/order'
import type { Page } from '@/types/product'
import { v4 as uuidv4 } from 'uuid'

export const ordersApi = {
  create: async (req: CreateOrderRequest): Promise<OrderResponse> => {
    const res = await apiClient.post<OrderResponse>('/orders', req, {
      headers: { 'Idempotency-Key': uuidv4() },
    })
    return res.data
  },

  getById: async (id: number): Promise<OrderResponse> => {
    const res = await apiClient.get<OrderResponse>(`/orders/${id}`)
    return res.data
  },

  getMyOrders: async (page = 0, size = 10): Promise<Page<OrderResponse>> => {
    const res = await apiClient.get<Page<OrderResponse>>('/orders/me', {
      params: { page, size },
    })
    return res.data
  },
}
