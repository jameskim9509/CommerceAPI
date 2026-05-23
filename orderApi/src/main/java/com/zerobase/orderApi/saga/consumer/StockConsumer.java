package com.zerobase.orderApi.saga.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobase.orderApi.domain.Order;
import com.zerobase.orderApi.exception.CustomException;
import com.zerobase.orderApi.repository.OrderRepository;
import com.zerobase.orderApi.saga.IdempotentEventHandler;
import com.zerobase.orderApi.saga.SagaEventPublisher;
import com.zerobase.orderApi.saga.SagaTopics;
import com.zerobase.orderApi.saga.event.SagaEvents;
import com.zerobase.orderApi.service.StockReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * ADR-003 forward step: PaymentDeducted 를 받아 재고 차감 + Order CONFIRMED.
 * 낙관적 락 충돌(ADR-002) 발생 시 StockReservationFailed 발행 → userApi 환불 트리거.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockConsumer {

    private static final String CONSUMER_NAME = "orderapi-stock";

    private final IdempotentEventHandler idempotentHandler;
    private final StockReservationService stockReservationService;
    private final OrderRepository orderRepository;
    private final SagaEventPublisher publisher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = SagaTopics.PAYMENT_DEDUCTED, groupId = CONSUMER_NAME)
    public void onPaymentDeducted(String json) throws JsonProcessingException {
        SagaEvents.PaymentDeducted event = objectMapper.readValue(json, SagaEvents.PaymentDeducted.class);
        idempotentHandler.handle(event.getEventId(), CONSUMER_NAME, event, this::process);
    }

    private void process(SagaEvents.PaymentDeducted event) {
        try {
            stockReservationService.reserveStock(event.getOrderId());
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Optimistic lock conflict during stock reservation for orderId={}", event.getOrderId(), e);
            publishStockReservationFailed(event.getOrderId(), "재고 동시성 충돌");
        } catch (CustomException e) {
            log.warn("Stock reservation failed for orderId={}: {}", event.getOrderId(), e.getMessage());
            publishStockReservationFailed(event.getOrderId(), e.getMessage());
        }
    }

    private void publishStockReservationFailed(Long orderId, String reason) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.error("Order not found while publishing StockReservationFailed: orderId={}", orderId);
            return;
        }
        publisher.publish(SagaTopics.STOCK_RESERVATION_FAILED, SagaEvents.StockReservationFailed.builder()
                .eventId(UUID.randomUUID())
                .orderId(orderId)
                .customerId(order.getCustomerId())
                .username(order.getUsername())
                .amountToRefund(order.getTotalPrice())
                .reason(reason)
                .build());
    }
}
