package com.zerobase.orderApi.saga.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobase.orderApi.domain.Order;
import com.zerobase.orderApi.exception.CustomException;
import com.zerobase.orderApi.exception.ErrorCode;
import com.zerobase.orderApi.repository.OrderRepository;
import com.zerobase.orderApi.saga.IdempotentEventHandler;
import com.zerobase.orderApi.saga.SagaTopics;
import com.zerobase.orderApi.saga.event.SagaEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR-003: SAGA 최종 단계의 Order 상태 전이.
 *  - PaymentFailed: 결제 자체 실패 → Order FAILED
 *  - PaymentReverted: 재고 충돌 후 환불 완료 → Order FAILED
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderStateConsumer {

    private final IdempotentEventHandler idempotentHandler;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = SagaTopics.PAYMENT_FAILED, groupId = "orderapi-order-state")
    public void onPaymentFailed(String json) throws JsonProcessingException {
        SagaEvents.PaymentFailed event = objectMapper.readValue(json, SagaEvents.PaymentFailed.class);
        idempotentHandler.handle(event.getEventId(), "orderapi-payment-failed", event, this::handlePaymentFailed);
    }

    @KafkaListener(topics = SagaTopics.PAYMENT_REVERTED, groupId = "orderapi-order-state")
    public void onPaymentReverted(String json) throws JsonProcessingException {
        SagaEvents.PaymentReverted event = objectMapper.readValue(json, SagaEvents.PaymentReverted.class);
        idempotentHandler.handle(event.getEventId(), "orderapi-payment-reverted", event, this::handlePaymentReverted);
    }

    @Transactional
    public void handlePaymentFailed(SagaEvents.PaymentFailed event) {
        Order order = orderRepository.findById(event.getOrderId())
                .orElseThrow(() -> new CustomException(ErrorCode.ORDER_NOT_FOUND));
        order.markFailed("결제 실패: " + event.getReason());
    }

    @Transactional
    public void handlePaymentReverted(SagaEvents.PaymentReverted event) {
        Order order = orderRepository.findById(event.getOrderId())
                .orElseThrow(() -> new CustomException(ErrorCode.ORDER_NOT_FOUND));
        order.markFailed("재고 차감 실패 후 환불 완료");
    }
}
