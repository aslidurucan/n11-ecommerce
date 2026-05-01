export interface ProductResponse {
  id: number
  name: string
  description: string
  basePrice: number
  brand: string
  category: string
  imageUrl?: string
  stockQuantity?: number
  translations?: ProductTranslation[]
}

export interface ProductTranslation {
  locale: string
  name: string
  description: string
}

export interface ProductFilterRequest {
  category?: string
  brand?: string
  minPrice?: number
  maxPrice?: number
  page?: number
  size?: number
  sort?: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}
