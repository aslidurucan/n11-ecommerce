package com.n11bootcamp.order.repository;

import com.n11bootcamp.order.entity.OutboxEvent;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "javax.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT e FROM OutboxEvent e
            WHERE e.published = false AND e.retryCount < :maxRetries
            ORDER BY e.createdAt ASC
            """)
    List<OutboxEvent> findUnpublishedForUpdate(@Param("maxRetries") int maxRetries,
                                                Pageable pageable);


    @Modifying(clearAutomatically = true)
    @Query("""
            DELETE FROM OutboxEvent e
            WHERE e.published = true AND e.createdAt < :cutoff
            """)
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);
}
