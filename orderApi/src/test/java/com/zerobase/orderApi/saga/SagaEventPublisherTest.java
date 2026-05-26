package com.zerobase.orderApi.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobase.orderApi.saga.event.SagaEvents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SagaEventPublisherTest {

    @Mock
    private OutboxEventRepository outboxRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SagaEventPublisher publisher;

    @Test
    @DisplayName("publish: Kafka 가 아니라 outbox_events 에 INSERT 한다 (sent_at=NULL)")
    void saves_to_outbox_not_kafka() {
        UUID eventId = UUID.randomUUID();
        SagaEvents.OrderCreated event = SagaEvents.OrderCreated.builder()
                .eventId(eventId)
                .orderId(1L)
                .customerId(2L)
                .username("u")
                .totalPrice(10000)
                .build();

        publisher.publish(SagaTopics.ORDER_CREATED, event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getTopic()).isEqualTo(SagaTopics.ORDER_CREATED);
        assertThat(saved.getPayload()).contains(eventId.toString());
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getSentAt()).isNull();   // 폴러가 채울 때까지 null
    }
}
