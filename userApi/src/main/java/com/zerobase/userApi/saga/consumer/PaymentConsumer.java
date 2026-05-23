package com.zerobase.userApi.saga.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobase.userApi.dto.ChangeBalanceDto;
import com.zerobase.userApi.exception.CustomException;
import com.zerobase.userApi.saga.IdempotentEventHandler;
import com.zerobase.userApi.saga.SagaEventPublisher;
import com.zerobase.userApi.saga.SagaTopics;
import com.zerobase.userApi.saga.event.SagaEvents;
import com.zerobase.userApi.service.customer.CustomerBalanceHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * ADR-003 forward step: OrderCreated 를 받아 잔액 차감 후 PaymentDeducted 발행.
 * 잔액 부족 등 비즈니스 실패는 PaymentFailed 발행으로 보상 트리거.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentConsumer {

    private static final String CONSUMER_NAME = "userapi-payment";

    private final IdempotentEventHandler idempotentHandler;
    private final CustomerBalanceHistoryService balanceService;
    private final SagaEventPublisher publisher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = SagaTopics.ORDER_CREATED, groupId = CONSUMER_NAME)
    public void onOrderCreated(String json) throws JsonProcessingException {
        SagaEvents.OrderCreated event = objectMapper.readValue(json, SagaEvents.OrderCreated.class);
        idempotentHandler.handle(event.getEventId(), CONSUMER_NAME, event, this::process);
    }

    private void process(SagaEvents.OrderCreated event) {
        ChangeBalanceDto.Input form = ChangeBalanceDto.Input.builder()
                .from(event.getUsername())
                .money(-event.getTotalPrice())
                .message("주문 결제 (orderId=" + event.getOrderId() + ")")
                .build();

        try {
            balanceService.changeBalance(event.getCustomerId(), form);
            publisher.publish(SagaTopics.PAYMENT_DEDUCTED, SagaEvents.PaymentDeducted.builder()
                    .eventId(UUID.randomUUID())
                    .orderId(event.getOrderId())
                    .build());
        } catch (CustomException e) {
            log.warn("Payment failed for orderId={}: {}", event.getOrderId(), e.getMessage());
            publisher.publish(SagaTopics.PAYMENT_FAILED, SagaEvents.PaymentFailed.builder()
                    .eventId(UUID.randomUUID())
                    .orderId(event.getOrderId())
                    .reason(e.getMessage())
                    .build());
        }
    }
}
