package com.zerobase.orderApi.saga;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxPollerTest {

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OutboxPoller poller;

    private OutboxEvent rowA;
    private OutboxEvent rowB;

    @BeforeEach
    void setup() {
        rowA = OutboxEvent.builder()
                .eventId(UUID.randomUUID())
                .topic("topic-a")
                .payload("{\"a\":1}")
                .createdAt(Instant.now().minusSeconds(2))
                .build();
        rowB = OutboxEvent.builder()
                .eventId(UUID.randomUUID())
                .topic("topic-b")
                .payload("{\"b\":2}")
                .createdAt(Instant.now().minusSeconds(1))
                .build();
    }

    @Test
    @DisplayName("정상 흐름: 각 미발행 row 를 send 하고 sent_at 마킹")
    void publishes_and_marks_sent() {
        given(outboxRepository.findTop100BySentAtIsNullOrderByCreatedAtAsc())
                .willReturn(List.of(rowA, rowB));
        given(kafkaTemplate.send(any(String.class), any(String.class), any(String.class)))
                .willReturn(CompletableFuture.completedFuture(null));

        poller.publishPending();

        verify(kafkaTemplate).send(eq("topic-a"), eq(rowA.getEventId().toString()), eq("{\"a\":1}"));
        verify(kafkaTemplate).send(eq("topic-b"), eq(rowB.getEventId().toString()), eq("{\"b\":2}"));
        assertThat(rowA.getSentAt()).isNotNull();
        assertThat(rowB.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("발행 실패 시 해당 row 및 이후 row 도 마킹하지 않고 break (순서 보장)")
    void stops_on_failure_preserves_order() {
        given(outboxRepository.findTop100BySentAtIsNullOrderByCreatedAtAsc())
                .willReturn(List.of(rowA, rowB));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Kafka down"));
        given(kafkaTemplate.send(eq("topic-a"), any(String.class), any(String.class)))
                .willReturn(failed);

        poller.publishPending();

        verify(kafkaTemplate).send(eq("topic-a"), any(String.class), any(String.class));
        verify(kafkaTemplate, never()).send(eq("topic-b"), any(String.class), any(String.class));
        assertThat(rowA.getSentAt()).isNull();
        assertThat(rowB.getSentAt()).isNull();
    }

    @Test
    @DisplayName("미발행 row 가 없으면 Kafka 호출도 없다")
    void no_op_when_empty() {
        given(outboxRepository.findTop100BySentAtIsNullOrderByCreatedAtAsc())
                .willReturn(List.of());

        poller.publishPending();

        verify(kafkaTemplate, never()).send(any(String.class), any(String.class), any(String.class));
    }
}
