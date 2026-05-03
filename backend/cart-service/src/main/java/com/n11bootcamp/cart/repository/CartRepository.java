package com.n11bootcamp.cart.repository;

import com.n11bootcamp.cart.model.Cart;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CartRepository {

    private static final String KEY_PREFIX = "cart:";
    private static final Duration TTL = Duration.ofDays(30);

    private final RedisTemplate<String, Cart> cartRedisTemplate;

    public Optional<Cart> findByUserId(String userId) {
        return Optional.ofNullable(cartRedisTemplate.opsForValue().get(key(userId)));
    }

    public void save(Cart cart) {
        cartRedisTemplate.opsForValue().set(key(cart.getUserId()), cart, TTL);
    }

    public void deleteByUserId(String userId) {
        cartRedisTemplate.delete(key(userId));
    }

    private String key(String userId) {
        return KEY_PREFIX + userId;
    }
}
