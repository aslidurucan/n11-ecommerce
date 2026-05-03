package com.n11bootcamp.chat.service;

import com.n11bootcamp.chat.dto.ChatRequest;
import com.n11bootcamp.chat.dto.ChatResponse;

public interface ChatService {
    ChatResponse search(ChatRequest request, String language);
}
