package com.n11bootcamp.order.service.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11bootcamp.order.dto.CardRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardCacheServiceImpl implements CardCacheService {

    private static final String KEY_PREFIX = "order:card:";
    private static final Duration TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void storeCard(Long orderId, CardRequest card) {
        try {
            String json = objectMapper.writeValueAsString(card);
            redisTemplate.opsForValue().set(KEY_PREFIX + orderId, json, TTL);
            log.debug("Card cached for order {} (TTL={}s)", orderId, TTL.getSeconds());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to cache card", e);
        }
    }

    @Override
    public CardRequest getCard(Long orderId) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + orderId);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, CardRequest.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize cached card for order {}", orderId);
            return null;
        }
    }

    @Override
    public void deleteCard(Long orderId) {
        redisTemplate.delete(KEY_PREFIX + orderId);
    }
}
