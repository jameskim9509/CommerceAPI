package com.zerobase.orderApi.saga;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IdempotentEventHandlerTest {

    @Mock
    private ProcessedEventRepository repository;

    @InjectMocks
    private IdempotentEventHandler handler;

    // [control/no-defense] ⑤ processed_events dedup 제거 — 중복 여부를 보지 않고 매번 재처리한다.
    @Disabled("[control/no-defense] ⑤ dedup 이 제거된 arm — 검증 대상 자체가 없음")
    @Test
    @DisplayName("이미 처리된 이벤트는 processor 호출 없이 skip")
    void skips_when_already_processed() {
        UUID eventId = UUID.randomUUID();
        given(repository.existsByEventIdAndConsumerName(eventId, "consumer-x")).willReturn(true);

        AtomicBoolean called = new AtomicBoolean(false);
        handler.handle(eventId, "consumer-x", "event-payload", e -> called.set(true));

        assertThat(called.get()).isFalse();
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // [control/no-defense] ⑤ processed_events dedup 제거 — ProcessedEvent 를 저장하지 않는다.
    @Disabled("[control/no-defense] ⑤ dedup 이 제거된 arm — 검증 대상 자체가 없음")
    @Test
    @DisplayName("처음 보는 이벤트는 processor 실행 후 ProcessedEvent 저장")
    void processes_and_saves_when_new() {
        UUID eventId = UUID.randomUUID();
        given(repository.existsByEventIdAndConsumerName(eventId, "consumer-x")).willReturn(false);

        AtomicBoolean called = new AtomicBoolean(false);
        handler.handle(eventId, "consumer-x", "event-payload", e -> called.set(true));

        assertThat(called.get()).isTrue();

        ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(repository).save(captor.capture());
        ProcessedEvent saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getConsumerName()).isEqualTo("consumer-x");
        assertThat(saved.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("eventId 가 null 이면 멱등성 체크 없이 processor 실행 (저장도 skip)")
    void no_eventId_runs_processor_without_persistence() {
        AtomicBoolean called = new AtomicBoolean(false);
        handler.handle(null, "consumer-x", "event-payload", e -> called.set(true));

        assertThat(called.get()).isTrue();
        verify(repository, never()).existsByEventIdAndConsumerName(org.mockito.ArgumentMatchers.any(), eq("consumer-x"));
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
