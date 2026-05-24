package com.zerobase.orderApi.saga.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobase.orderApi.domain.Order;
import com.zerobase.orderApi.domain.OrderStatus;
import com.zerobase.orderApi.exception.CustomException;
import com.zerobase.orderApi.exception.ErrorCode;
import com.zerobase.orderApi.repository.OrderRepository;
import com.zerobase.orderApi.saga.IdempotentEventHandler;
import com.zerobase.orderApi.saga.SagaEventPublisher;
import com.zerobase.orderApi.saga.SagaTopics;
import com.zerobase.orderApi.saga.event.SagaEvents;
import com.zerobase.orderApi.service.StockReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockConsumerTest {

    @Mock private IdempotentEventHandler idempotentHandler;
    @Mock private StockReservationService stockReservationService;
    @Mock private OrderRepository orderRepository;
    @Mock private SagaEventPublisher publisher;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StockConsumer consumer;

    @BeforeEach
    void init() {
        consumer = new StockConsumer(idempotentHandler, stockReservationService, orderRepository, publisher, objectMapper);
        doAnswer(invocation -> {
            Consumer<Object> processor = invocation.getArgument(3);
            processor.accept(invocation.getArgument(2));
            return null;
        }).when(idempotentHandler).handle(any(), any(), any(), any());
    }

    @Test
    @DisplayName("정상 reserveStock: 어떤 이벤트도 발행하지 않음 (Order 는 내부에서 CONFIRMED)")
    void no_event_on_success() throws Exception {
        SagaEvents.PaymentDeducted event = SagaEvents.PaymentDeducted.builder()
                .eventId(UUID.randomUUID())
                .orderId(42L)
                .build();

        consumer.onPaymentDeducted(objectMapper.writeValueAsString(event));

        verify(stockReservationService).reserveStock(42L);
        verify(publisher, never()).publish(any(), any());
    }

    @Test
    @DisplayName("낙관적 락 충돌: StockReservationFailed 발행 (사유=재고 동시성 충돌)")
    void publishes_StockReservationFailed_on_optimistic_lock_conflict() throws Exception {
        doThrow(new ObjectOptimisticLockingFailureException(Order.class, 42L))
                .when(stockReservationService).reserveStock(42L);

        Order order = Order.builder()
                .customerId(7L)
                .username("user")
                .status(OrderStatus.PENDING)
                .totalPrice(10000)
                .build();
        given(orderRepository.findById(42L)).willReturn(Optional.of(order));

        SagaEvents.PaymentDeducted event = SagaEvents.PaymentDeducted.builder()
                .eventId(UUID.randomUUID())
                .orderId(42L)
                .build();

        consumer.onPaymentDeducted(objectMapper.writeValueAsString(event));

        ArgumentCaptor<SagaEvents.StockReservationFailed> captor =
                ArgumentCaptor.forClass(SagaEvents.StockReservationFailed.class);
        verify(publisher).publish(eq(SagaTopics.STOCK_RESERVATION_FAILED), captor.capture());

        SagaEvents.StockReservationFailed published = captor.getValue();
        assertThat(published.getOrderId()).isEqualTo(42L);
        assertThat(published.getCustomerId()).isEqualTo(7L);
        assertThat(published.getAmountToRefund()).isEqualTo(10000);
        assertThat(published.getReason()).contains("재고");
    }

    @Test
    @DisplayName("재고 부족(CustomException): StockReservationFailed 발행 (사유=에러 메시지)")
    void publishes_StockReservationFailed_on_business_failure() throws Exception {
        doThrow(new CustomException(ErrorCode.NOT_ENOUGH_ITEM_COUNT))
                .when(stockReservationService).reserveStock(42L);

        Order order = Order.builder()
                .customerId(7L)
                .username("user")
                .status(OrderStatus.PENDING)
                .totalPrice(10000)
                .build();
        given(orderRepository.findById(42L)).willReturn(Optional.of(order));

        SagaEvents.PaymentDeducted event = SagaEvents.PaymentDeducted.builder()
                .eventId(UUID.randomUUID())
                .orderId(42L)
                .build();

        consumer.onPaymentDeducted(objectMapper.writeValueAsString(event));

        verify(publisher).publish(eq(SagaTopics.STOCK_RESERVATION_FAILED), any(SagaEvents.StockReservationFailed.class));
    }
}
