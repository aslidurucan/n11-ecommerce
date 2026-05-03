package com.n11bootcamp.stock.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "stock_reservations",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_reservation_order_product",
        columnNames = {"order_id", "product_id"}
    )
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;
}
