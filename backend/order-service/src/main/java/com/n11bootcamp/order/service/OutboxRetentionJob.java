package com.n11bootcamp.order.service;

import com.n11bootcamp.order.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRetentionJob {

    private final OutboxEventRepository outboxEventRepository;

    @Value("${outbox.retention.days:7}")
    private int retentionDays;

    @Scheduled(cron = "${outbox.retention.cron:0 0 2 * * *}")
    @Transactional
    public void cleanupPublishedEvents() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        int deleted = outboxEventRepository.deletePublishedBefore(cutoff);

        if (deleted > 0) {
            log.info("[OUTBOX-RETENTION] Deleted {} published events older than {} days", deleted, retentionDays);
        } else {
            log.debug("[OUTBOX-RETENTION] No events to clean up");
        }
    }
}
