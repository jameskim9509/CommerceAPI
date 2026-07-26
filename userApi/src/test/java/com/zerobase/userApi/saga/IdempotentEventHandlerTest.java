package com.zerobase.userApi.saga;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// [control/no-defense] ⑤ processed_events dedup 제거 — handle() 이 중복 여부를 보지 않고
// 매번 재처리하며 ProcessedEvent 도 저장하지 않는다. 낙관적 락 재시도 테스트도 ④ @Version
// 제거로 성립하지 않는다. 방어가 있는 arm(main·feature)에서는 그대로 살아 있다.
@Disabled("[control/no-defense] ⑤ dedup·④ 낙관적 락이 제거된 arm — 검증 대상 자체가 없음")
@ExtendWith(MockitoExtension.class)
class IdempotentEventHandlerTest {

    @Mock
    private ProcessedEventRepository repository;

    // ADR-002 잔액 낙관적 락 재시도를 위해 TransactionTemplate 을 생성자에서 만든다.
    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private IdempotentEventHandler handler;

    @Test
    @DisplayName("이미 처리된 이벤트는 processor 호출 없이 skip")
    void skips_when_already_processed() {
        UUID eventId = UUID.randomUUID();
        given(repository.existsByEventIdAndConsumerName(eventId, "consumer-x")).willReturn(true);

        AtomicBoolean called = new AtomicBoolean(false);
        handler.handle(eventId, "consumer-x", "event", e -> called.set(true));

        assertThat(called.get()).isFalse();
        verify(repository, never()).save(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("처음 보는 이벤트는 processor 실행 후 ProcessedEvent 저장")
    void processes_and_saves_when_new() {
        UUID eventId = UUID.randomUUID();
        given(repository.existsByEventIdAndConsumerName(eventId, "consumer-x")).willReturn(false);

        AtomicBoolean called = new AtomicBoolean(false);
        handler.handle(eventId, "consumer-x", "event", e -> called.set(true));

        assertThat(called.get()).isTrue();

        ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(repository).save(captor.capture());
        ProcessedEvent saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getConsumerName()).isEqualTo("consumer-x");
        assertThat(saved.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("낙관적 락 충돌(commit 실패) 시 트랜잭션 경계에서 재시도하여 결국 성공")
    void retries_on_optimistic_lock_conflict() {
        UUID eventId = UUID.randomUUID();
        given(repository.existsByEventIdAndConsumerName(eventId, "consumer-x")).willReturn(false);
        // 첫 commit 은 낙관적 락 충돌, 두 번째 commit 은 성공
        willThrow(new ObjectOptimisticLockingFailureException("balance version conflict", new RuntimeException()))
                .willDoNothing()
                .given(transactionManager).commit(ArgumentMatchers.any());

        AtomicInteger processorCalls = new AtomicInteger();
        handler.handle(eventId, "consumer-x", "event", e -> processorCalls.incrementAndGet());

        // 첫 시도는 commit 충돌로 롤백 → 재시도에서 성공 (processor 는 시도마다 재실행)
        assertThat(processorCalls.get()).isEqualTo(2);
        verify(repository, times(2)).save(ArgumentMatchers.any());
    }
}
