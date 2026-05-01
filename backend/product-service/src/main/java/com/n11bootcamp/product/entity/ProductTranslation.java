package com.n11bootcamp.product.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "product_translations",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_product_translation",
        columnNames = {"product_id", "language"}
    )
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Bidirectional ilişki: ProductTranslation "sahibi" (FK'yı o taşır)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "language", nullable = false, length = 5)
    private String language;  // "tr", "en", "de"

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
