import { apiClient } from './axios'
import type { ProductResponse, ProductFilterRequest, Page } from '@/types/product'

export const productsApi = {
  getAll: async (filters: ProductFilterRequest = {}): Promise<Page<ProductResponse>> => {
    const params: Record<string, string | number> = {}
    if (filters.category) params.category = filters.category
    if (filters.brand) params.brand = filters.brand
    if (filters.minPrice !== undefined) params.minPrice = filters.minPrice
    if (filters.maxPrice !== undefined) params.maxPrice = filters.maxPrice
    params.page = filters.page ?? 0
    params.size = filters.size ?? 12
    if (filters.sort) params.sort = filters.sort

    const res = await apiClient.get<Page<ProductResponse>>('/products', { params })
    return res.data
  },

  getById: async (id: number): Promise<ProductResponse> => {
    const res = await apiClient.get<ProductResponse>(`/products/${id}`)
    return res.data
  },
}
