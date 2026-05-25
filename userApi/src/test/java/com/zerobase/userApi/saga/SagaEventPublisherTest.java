package com.zerobase.userApi.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobase.userApi.saga.event.SagaEvents;
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
    @DisplayName("publish: outbox_events 에 INSERT 만 한다 (sent_at=NULL)")
    void saves_to_outbox_not_kafka() {
        UUID eventId = UUID.randomUUID();
        SagaEvents.PaymentDeducted event = SagaEvents.PaymentDeducted.builder()
                .eventId(eventId)
                .orderId(42L)
                .build();

        publisher.publish(SagaTopics.PAYMENT_DEDUCTED, event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getTopic()).isEqualTo(SagaTopics.PAYMENT_DEDUCTED);
        assertThat(saved.getPayload()).contains(eventId.toString());
        assertThat(saved.getSentAt()).isNull();
    }
}
