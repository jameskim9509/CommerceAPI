package com.zerobase.orderApi.saga.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobase.orderApi.domain.Order;
import com.zerobase.orderApi.domain.OrderStatus;
import com.zerobase.orderApi.repository.OrderRepository;
import com.zerobase.orderApi.saga.IdempotentEventHandler;
import com.zerobase.orderApi.saga.event.SagaEvents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class OrderStateConsumerTest {

    @Mock private IdempotentEventHandler idempotentHandler;
    @Mock private OrderRepository orderRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OrderStateConsumer consumer;

    @BeforeEach
    void init() {
        consumer = new OrderStateConsumer(idempotentHandler, orderRepository, objectMapper);
        doAnswer(invocation -> {
            Consumer<Object> processor = invocation.getArgument(3);
            processor.accept(invocation.getArgument(2));
            return null;
        }).when(idempotentHandler).handle(any(), any(), any(), any());
    }

    @Test
    @DisplayName("PaymentFailed: Order(PENDING) → FAILED + 결제 실패 사유 기록")
    void handlePaymentFailed_marks_order_failed() throws Exception {
        Order order = Order.builder()
                .customerId(7L)
                .username("user")
                .status(OrderStatus.PENDING)
                .totalPrice(10000)
                .build();
        given(orderRepository.findById(42L)).willReturn(Optional.of(order));

        SagaEvents.PaymentFailed event = SagaEvents.PaymentFailed.builder()
                .eventId(UUID.randomUUID())
                .orderId(42L)
                .reason("잔액 부족")
                .build();

        consumer.onPaymentFailed(objectMapper.writeValueAsString(event));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(order.getFailureReason()).contains("결제 실패").contains("잔액 부족");
    }

    @Test
    @DisplayName("PaymentReverted: Order(PAID) → FAILED + 환불 완료 사유 기록")
    void handlePaymentReverted_marks_order_failed() throws Exception {
        Order order = Order.builder()
                .customerId(7L)
                .username("user")
                .status(OrderStatus.PAID)
                .totalPrice(10000)
                .build();
        given(orderRepository.findById(42L)).willReturn(Optional.of(order));

        SagaEvents.PaymentReverted event = SagaEvents.PaymentReverted.builder()
                .eventId(UUID.randomUUID())
                .orderId(42L)
                .build();

        consumer.onPaymentReverted(objectMapper.writeValueAsString(event));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(order.getFailureReason()).contains("환불");
    }
}
