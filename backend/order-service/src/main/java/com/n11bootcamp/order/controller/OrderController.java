package com.n11bootcamp.order.controller;

import com.n11bootcamp.order.dto.CreateOrderRequest;
import com.n11bootcamp.order.dto.OrderResponse;
import com.n11bootcamp.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Sipariş yönetimi")
public class OrderController {

    private static final String HEADER_USER_ID    = "X-User-Id";
    private static final String HEADER_USERNAME   = "X-User-Username";
    private static final String HEADER_IDEMPOTENCY = "Idempotency-Key";

    private final OrderService orderService;

    @Operation(
            summary = "Yeni sipariş oluştur",
            description = "Idempotency-Key header ile çağrılır. Aynı key'le tekrar çağrıldığında yeni sipariş oluşturulmaz, mevcut döner."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Sipariş başarıyla oluşturuldu"),
            @ApiResponse(responseCode = "400", description = "Geçersiz istek verisi (validation hatası)",
                    content = @Content(schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Kimlik doğrulama gerekli")
    })
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader(HEADER_USER_ID) String userId,
            @RequestHeader(value = HEADER_USERNAME, required = false) String username,
            @RequestHeader(HEADER_IDEMPOTENCY) String idempotencyKey,
            @RequestBody @Valid CreateOrderRequest request) {

        // idempotencyKey null gelebilir — karar OrderService içinde verilir (iş mantığı)
        OrderResponse response = orderService.createOrder(userId, username, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Sipariş detayı")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sipariş bulundu"),
            @ApiResponse(responseCode = "404", description = "Sipariş bulunamadı")
    })
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    @Operation(summary = "Kendi siparişlerim — sayfalı")
    @ApiResponse(responseCode = "200", description = "Sipariş listesi")
    @GetMapping("/me")
    public ResponseEntity<Page<OrderResponse>> myOrders(
            @RequestHeader(HEADER_USER_ID) String userId,
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(orderService.findUserOrders(userId, pageable));
    }

    @Operation(summary = "Tüm siparişler — sadece ADMIN")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tüm siparişler"),
            @ApiResponse(responseCode = "403", description = "ADMIN yetkisi gerekli")
    })
    @GetMapping
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    public ResponseEntity<Page<OrderResponse>> allOrders(
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(orderService.findAllOrders(pageable));
    }
}