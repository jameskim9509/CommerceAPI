package com.zerobase.userApi.saga;

import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

@Component
@Slf4j
public class IdempotentEventHandler {

    // ADR-002 잔액 낙관적 락 충돌 시 트랜잭션 경계에서 재시도하는 최대 횟수.
    private static final int MAX_ATTEMPTS = 30;

    private final ProcessedEventRepository processedEventRepository;
    private final TransactionTemplate transactionTemplate;

    public IdempotentEventHandler(ProcessedEventRepository processedEventRepository,
                                  PlatformTransactionManager transactionManager) {
        this.processedEventRepository = processedEventRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 처리 + ProcessedEvent 기록을 한 트랜잭션으로 묶어 멱등성을 보장한다.
     * 잔액 @Version(ADR-002) 으로 동시 결제·환불이 충돌하면 commit 시점에
     * ObjectOptimisticLockingFailureException 이 발생 → 트랜잭션 전체 롤백(ProcessedEvent 포함)
     * → 새 트랜잭션으로 재시도(최신 version·잔액 재조회). 재시도해도 ProcessedEvent 가
     * 같은 트랜잭션 안이라 중복 처리되지 않는다.
     */
    public <E> void handle(UUID eventId, String consumerName, E event, Consumer<E> processor) {
        for (int attempt = 1; ; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(status ->
                        doHandle(eventId, consumerName, event, processor));
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    log.error("Optimistic lock retry exhausted ({}x) for event={} consumer={}",
                            MAX_ATTEMPTS, eventId, consumerName);
                    throw e;
                }
                log.debug("Optimistic lock conflict (attempt {}) event={} consumer={}, retrying",
                        attempt, eventId, consumerName);
            }
        }
    }

    private <E> void doHandle(UUID eventId, String consumerName, E event, Consumer<E> processor) {
        if (eventId == null) {
            log.warn("Event without eventId received for consumer={}, processing without idempotency", consumerName);
            processor.accept(event);
            return;
        }
        if (processedEventRepository.existsByEventIdAndConsumerName(eventId, consumerName)) {
            log.info("Event {} already processed by {}, skipping", eventId, consumerName);
            return;
        }
        processor.accept(event);
        processedEventRepository.save(ProcessedEvent.builder()
                .eventId(eventId)
                .consumerName(consumerName)
                .processedAt(Instant.now())
                .build());
    }
}
