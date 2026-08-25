package com.researchflow.subscription;

import com.researchflow.persistence.TopicSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SubscriptionScheduler {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionScheduler.class);
    private final TopicSubscriptionRepository repository;
    private final SubscriptionService service;

    public SubscriptionScheduler(TopicSubscriptionRepository repository, SubscriptionService service) {
        this.repository = repository;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${research-flow.subscription.scan-interval-ms:60000}")
    public void runDueSubscriptions() {
        repository.findByEnabledTrueAndNextRunAtLessThanEqual(Instant.now()).forEach(subscription -> {
            try {
                service.run(subscription.getId());
            } catch (Exception exception) {
                log.warn("Subscription {} execution failed: {}", subscription.getId(), exception.getMessage());
                service.defer(subscription.getId());
            }
        });
    }
}
