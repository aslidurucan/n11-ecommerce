package com.n11bootcamp.stock.controller;

import com.n11bootcamp.stock.dto.StockResponse;
import com.n11bootcamp.stock.dto.UpdateStockRequest;
import com.n11bootcamp.stock.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Tag(name = "Stock", description = "Stok yönetimi API")
public class StockController {

    private final StockService stockService;

    @GetMapping("/{productId}")
    @Operation(summary = "Ürün stok bilgisini getir")
    public ResponseEntity<StockResponse> getStock(@PathVariable Long productId) {
        return ResponseEntity.ok(stockService.getStock(productId));
    }

    @PutMapping
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    @Operation(summary = "Stok miktarını set et (upsert)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<StockResponse> setStock(@Valid @RequestBody UpdateStockRequest request) {
        return ResponseEntity.ok(stockService.setStock(request));
    }

    @PatchMapping("/{productId}/increase")
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    @Operation(summary = "Stok artır — depo girişi, iade vb.", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<StockResponse> increaseStock(
            @PathVariable Long productId,
            @RequestParam int delta) {
        return ResponseEntity.ok(stockService.increaseStock(productId, delta));
    }

    @PatchMapping("/{productId}/decrease")
    @PreAuthorize("hasRole('CLIENT_ADMIN')")
    @Operation(summary = "Stok azalt — fire, kayıp vb.", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<StockResponse> decreaseStock(
            @PathVariable Long productId,
            @RequestParam int delta) {
        return ResponseEntity.ok(stockService.decreaseStock(productId, delta));
    }
}
