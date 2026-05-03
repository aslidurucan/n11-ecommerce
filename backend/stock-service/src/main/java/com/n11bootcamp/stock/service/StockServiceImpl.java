package com.n11bootcamp.stock.service;

import com.n11bootcamp.stock.dto.ReservationItem;
import com.n11bootcamp.stock.dto.StockResponse;
import com.n11bootcamp.stock.dto.UpdateStockRequest;
import com.n11bootcamp.stock.entity.ProductStock;
import com.n11bootcamp.stock.entity.StockReservation;
import com.n11bootcamp.stock.exception.StockNotFoundException;
import com.n11bootcamp.stock.mapper.StockMapper;
import com.n11bootcamp.stock.repository.ProductStockRepository;
import com.n11bootcamp.stock.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class StockServiceImpl implements StockService {

    private final ProductStockRepository stockRepo;
    private final StockReservationRepository reservationRepo;
    private final StockMapper stockMapper;


    @Override
    @Cacheable(value = "stocks", key = "#productId")
    @Transactional(readOnly = true)
    public StockResponse getStock(Long productId) {
        ProductStock stock = findStockOrThrow(productId);
        return stockMapper.toResponse(stock);
    }

    @Override
    @CacheEvict(value = "stocks", key = "#request.productId")
    @Transactional
    public StockResponse setStock(UpdateStockRequest request) {
        ProductStock stock = stockRepo.findById(request.getProductId())
                .orElseGet(() -> buildNewStock(request.getProductId()));

        stock.setAvailableQuantity(request.getQuantity());
        stockRepo.save(stock);

        log.info("Stock set: productId={}, quantity={}", request.getProductId(), request.getQuantity());
        return stockMapper.toResponse(stock);
    }

    @Override
    @CacheEvict(value = "stocks", key = "#productId")
    @Transactional
    public StockResponse increaseStock(Long productId, int delta) {
        validatePositiveDelta(delta, "increase");

        ProductStock stock = findStockForUpdateOrThrow(productId);
        stock.setAvailableQuantity(stock.getAvailableQuantity() + delta);
        stockRepo.save(stock);

        log.info("Stock increased: productId={}, delta={}, total={}", productId, delta, stock.getAvailableQuantity());
        return stockMapper.toResponse(stock);
    }

    @Override
    @CacheEvict(value = "stocks", key = "#productId")
    @Transactional
    public StockResponse decreaseStock(Long productId, int delta) {
        validatePositiveDelta(delta, "decrease");

        ProductStock stock = findStockForUpdateOrThrow(productId);
        validateSufficientStockForDecrease(stock, delta);

        stock.setAvailableQuantity(stock.getAvailableQuantity() - delta);
        stockRepo.save(stock);

        log.info("Stock decreased: productId={}, delta={}, remaining={}", productId, delta, stock.getAvailableQuantity());
        return stockMapper.toResponse(stock);
    }


    @Override
    @Transactional
    public List<Long> reserveStock(Long orderId, List<ReservationItem> items) {
        if (isAlreadyReserved(orderId)) {
            log.warn("[STOCK] Order {} already reserved — idempotency skip", orderId);
            return List.of();
        }

        Map<Long, ProductStock> stockMap = loadStocksWithLock(items);

        List<Long> insufficientIds = findInsufficientStocks(items, stockMap);
        if (!insufficientIds.isEmpty()) {
            log.warn("[STOCK] Insufficient stock for order {}: products={}", orderId, insufficientIds);
            return insufficientIds;
        }

        applyReservations(orderId, items, stockMap);

        log.info("[STOCK] Reserved {} items for order {}", items.size(), orderId);
        return List.of();
    }

    @Override
    @Transactional
    public void releaseStock(Long orderId) {
        List<StockReservation> reservations = reservationRepo.findByOrderIdForUpdate(orderId);

        if (reservations.isEmpty()) {
            log.warn("[STOCK] No reservations for order {} — already released or never reserved", orderId);
            return;
        }

        Map<Long, ProductStock> stockMap = loadReservationStocksWithLock(reservations);

        releaseReservedUnits(reservations, stockMap);
        reservationRepo.deleteByOrderId(orderId);

        log.info("[STOCK] Compensation complete for order {}", orderId);
    }

    @Override
    @Transactional
    public void commitReservation(Long orderId) {
        List<StockReservation> reservations = reservationRepo.findByOrderIdForUpdate(orderId);

        if (reservations.isEmpty()) {
            log.warn("[STOCK] No reservations for order {} — already committed or never reserved", orderId);
            return;
        }

        Map<Long, ProductStock> stockMap = loadReservationStocksWithLock(reservations);

        confirmReservedUnits(reservations, stockMap);
        reservationRepo.deleteByOrderId(orderId);

        log.info("[STOCK] Commit complete for order {}", orderId);
    }

    private void confirmReservedUnits(List<StockReservation> reservations,
                                      Map<Long, ProductStock> stockMap) {
        for (StockReservation reservation : reservations) {
            ProductStock stock = stockMap.get(reservation.getProductId());
            if (stock != null) {
                stock.confirmReservation(reservation.getQuantity()); // entity içindeki iş kuralı
                log.info("[STOCK] Committed {} units of product {} for order {}",
                        reservation.getQuantity(), reservation.getProductId(), reservation.getOrderId());
            } else {
                log.warn("[STOCK] ProductStock not found during commit: productId={}",
                        reservation.getProductId());
            }
        }
    }


    private ProductStock buildNewStock(Long productId) {
        return ProductStock.builder()
                .productId(productId)
                .availableQuantity(0)
                .reservedQuantity(0)
                .build();
    }


    private void validatePositiveDelta(int delta, String operation) {
        if (delta <= 0) {
            throw new IllegalArgumentException(
                    "Delta must be positive for " + operation + ", got: " + delta);
        }
    }

    private void validateSufficientStockForDecrease(ProductStock stock, int delta) {
        if (stock.getAvailableQuantity() < delta) {
            throw new IllegalStateException(
                    "Insufficient stock to decrease: available="
                            + stock.getAvailableQuantity() + ", requested=" + delta);
        }
    }


    private boolean isAlreadyReserved(Long orderId) {
        return reservationRepo.existsByOrderId(orderId);
    }

    private Map<Long, ProductStock> loadStocksWithLock(List<ReservationItem> items) {
        List<Long> sortedIds = items.stream()
                .map(ReservationItem::productId)
                .sorted()
                .toList();

        return stockRepo.findAllByIdForUpdate(sortedIds).stream()
                .collect(Collectors.toMap(ProductStock::getProductId, stock -> stock));
    }

    private Map<Long, ProductStock> loadReservationStocksWithLock(List<StockReservation> reservations) {
        List<Long> sortedIds = reservations.stream()
                .map(StockReservation::getProductId)
                .sorted()
                .toList();

        return stockRepo.findAllByIdForUpdate(sortedIds).stream()
                .collect(Collectors.toMap(ProductStock::getProductId, stock -> stock));
    }

    private List<Long> findInsufficientStocks(List<ReservationItem> items,
                                              Map<Long, ProductStock> stockMap) {
        return items.stream()
                .filter(item -> {
                    ProductStock stock = stockMap.get(item.productId());
                    return stock == null || !stock.canReserve(item.quantity());
                })
                .map(ReservationItem::productId)
                .toList();
    }

    private void applyReservations(Long orderId, List<ReservationItem> items,
                                   Map<Long, ProductStock> stockMap) {
        for (ReservationItem item : items) {
            ProductStock stock = stockMap.get(item.productId());
            stock.reserve(item.quantity()); // entity içindeki iş kuralı

            reservationRepo.save(StockReservation.builder()
                    .orderId(orderId)
                    .productId(item.productId())
                    .quantity(item.quantity())
                    .build());
        }
    }

    private void releaseReservedUnits(List<StockReservation> reservations,
                                      Map<Long, ProductStock> stockMap) {
        for (StockReservation reservation : reservations) {
            ProductStock stock = stockMap.get(reservation.getProductId());
            if (stock != null) {
                stock.release(reservation.getQuantity()); // entity içindeki iş kuralı
                log.info("[STOCK] Released {} units of product {} for order {}",
                        reservation.getQuantity(), reservation.getProductId(), reservation.getOrderId());
            } else {
                log.warn("[STOCK] ProductStock not found during release: productId={}",
                        reservation.getProductId());
            }
        }
    }

    private ProductStock findStockOrThrow(Long productId) {
        return stockRepo.findById(productId)
                .orElseThrow(() -> new StockNotFoundException(productId));
    }

    private ProductStock findStockForUpdateOrThrow(Long productId) {
        return stockRepo.findByIdForUpdate(productId)
                .orElseThrow(() -> new StockNotFoundException(productId));
    }
}