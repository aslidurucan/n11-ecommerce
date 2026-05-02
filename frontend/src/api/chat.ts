import { apiClient } from './axios'
import type { ChatRequest, ChatResponse } from '@/types/chat'

export const chatApi = {
    search: async (request: ChatRequest, language: string = 'tr'): Promise<ChatResponse> => {
        const res = await apiClient.post<ChatResponse>('/v1/chat/search', request, {
            headers: { 'Accept-Language': language },
        })
        return res.data
    },
}