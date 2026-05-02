
export interface ChatRequest {
    query: string
}

export interface InterpretedFilter {
    category: string | null
    brand: string | null
    minPrice: number | null
    maxPrice: number | null
}

export interface ChatProductSummary {
    id: number
    name: string
    category: string | null
    brand: string | null
    basePrice: number
    imageUrl: string | null
}

export interface ChatResponse {
    reply: string
    interpretedFilter: InterpretedFilter
    products: ChatProductSummary[]
}