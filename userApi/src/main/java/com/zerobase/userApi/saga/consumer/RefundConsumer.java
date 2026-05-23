package com.zerobase.userApi.saga.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobase.userApi.dto.ChangeBalanceDto;
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
 * ADR-003 compensation step: StockReservationFailed 를 받아 결제를 환불하고
 * PaymentReverted 발행. 보상 처리 자체는 멱등성 + broker 재시도로 보장.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefundConsumer {

    private static final String CONSUMER_NAME = "userapi-refund";

    private final IdempotentEventHandler idempotentHandler;
    private final CustomerBalanceHistoryService balanceService;
    private final SagaEventPublisher publisher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = SagaTopics.STOCK_RESERVATION_FAILED, groupId = CONSUMER_NAME)
    public void onStockReservationFailed(String json) throws JsonProcessingException {
        SagaEvents.StockReservationFailed event =
                objectMapper.readValue(json, SagaEvents.StockReservationFailed.class);
        idempotentHandler.handle(event.getEventId(), CONSUMER_NAME, event, this::process);
    }

    private void process(SagaEvents.StockReservationFailed event) {
        ChangeBalanceDto.Input form = ChangeBalanceDto.Input.builder()
                .from(event.getUsername())
                .money(event.getAmountToRefund())
                .message("주문 실패 환불 (orderId=" + event.getOrderId() + ", reason=" + event.getReason() + ")")
                .build();

        balanceService.changeBalance(event.getCustomerId(), form);
        publisher.publish(SagaTopics.PAYMENT_REVERTED, SagaEvents.PaymentReverted.builder()
                .eventId(UUID.randomUUID())
                .orderId(event.getOrderId())
                .build());
    }
}
