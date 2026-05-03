package com.n11bootcamp.order.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "orders",
    indexes = {
        @Index(name = "idx_orders_user_id", columnList = "user_id"),
        @Index(name = "idx_orders_status", columnList = "status"),
        @Index(name = "idx_orders_created_at", columnList = "created_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_orders_idempotency_key", columnNames = "idempotency_key")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "username")
    private String username;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "TRY";

    @Column(name = "ship_first_name") private String shipFirstName;
    @Column(name = "ship_last_name")  private String shipLastName;
    @Column(name = "ship_email")      private String shipEmail;
    @Column(name = "ship_phone")      private String shipPhone;
    @Column(name = "ship_address", length = 500) private String shipAddress;
    @Column(name = "ship_city")       private String shipCity;
    @Column(name = "ship_country")    private String shipCountry;

    @Column(name = "payment_id", length = 128)
    private String paymentId;

    @Column(name = "payment_failure_reason", length = 500)
    private String paymentFailureReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    public void addItem(OrderItem item) {
        item.setOrder(this);
        this.items.add(item);
    }


    public void transitionTo(OrderStatus newStatus) {
        if (this.status != null && this.status.isTerminal()) {
            throw new IllegalStateException(
                "Order " + id + " is in terminal state " + status + ", cannot transition to " + newStatus);
        }
        this.status = newStatus;
    }
}
