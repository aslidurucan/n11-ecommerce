package com.n11bootcamp.chat.controller;

import com.n11bootcamp.chat.dto.ChatRequest;
import com.n11bootcamp.chat.dto.ChatResponse;
import com.n11bootcamp.chat.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "AI destekli ürün arama")
public class ChatController {

    private static final String DEFAULT_LANG = "tr";

    private final ChatService chatService;

    @PostMapping("/search")
    @Operation(
            summary = "Doğal dil ile ürün ara",
            description = "Kullanıcının yazdığı serbest metni LLM ile analiz edip "
                    + "uygun ürünleri product-service'ten döndürür."
    )
    public ResponseEntity<ChatResponse> search(
            @Valid @RequestBody ChatRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = DEFAULT_LANG) String language
    ) {
        return ResponseEntity.ok(chatService.search(request, language));
    }
}