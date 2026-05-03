package com.n11bootcamp.cart.exception;

import com.n11bootcamp.common.exception.BaseExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(CartItemNotFoundException.class)
    public ProblemDetail handleCartItemNotFound(CartItemNotFoundException ex) {
        log.warn("Cart item not found: {}", ex.getMessage());
        return problemDetail(HttpStatus.NOT_FOUND, ex.getMessage(), "not-found");
    }

    @ExceptionHandler(ProductNotAvailableException.class)
    public ProblemDetail handleProductNotAvailable(ProductNotAvailableException ex) {
        log.warn("Product not available: {}", ex.getMessage());
        return problemDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), "product-unavailable");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        log.error("Service unavailable: {}", ex.getMessage(), ex);
        return problemDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), "service-unavailable");
    }
}
