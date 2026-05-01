package com.n11bootcamp.stock.repository;

import com.n11bootcamp.stock.entity.StockReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    List<StockReservation> findByOrderId(Long orderId);

    /**
     * Pessimistic lock ile rezervasyonları okur.
     * releaseStock'ta paralel duplicate event'lere karşı race condition koruması.
     *
     * <p>RabbitMQ at-least-once garantisi: aynı PaymentFailedEvent iki kez gelebilir.
     * Her iki listener da aynı anda çalışırsa, biri kilidi alır, işlemini bitirir,
     * rezervasyonları siler. Diğeri kilidi aldığında boş liste görür, idempotent çıkar.</p>
     *
     * SELECT ... FROM stock_reservations WHERE order_id = ? FOR UPDATE
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM StockReservation r WHERE r.orderId = :orderId")
    List<StockReservation> findByOrderIdForUpdate(@Param("orderId") Long orderId);

    boolean existsByOrderId(Long orderId);

    void deleteByOrderId(Long orderId);
}
