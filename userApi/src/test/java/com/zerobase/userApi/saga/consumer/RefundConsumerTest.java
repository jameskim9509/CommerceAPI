package com.zerobase.userApi.saga.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobase.userApi.dto.ChangeBalanceDto;
import com.zerobase.userApi.saga.IdempotentEventHandler;
import com.zerobase.userApi.saga.SagaEventPublisher;
import com.zerobase.userApi.saga.SagaTopics;
import com.zerobase.userApi.saga.event.SagaEvents;
import com.zerobase.userApi.service.customer.CustomerBalanceHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundConsumerTest {

    @Mock private IdempotentEventHandler idempotentHandler;
    @Mock private CustomerBalanceHistoryService balanceService;
    @Mock private SagaEventPublisher publisher;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RefundConsumer consumer;

    @BeforeEach
    void init() {
        consumer = new RefundConsumer(idempotentHandler, balanceService, publisher, objectMapper);
        doAnswer(invocation -> {
            Consumer<Object> processor = invocation.getArgument(3);
            processor.accept(invocation.getArgument(2));
            return null;
        }).when(idempotentHandler).handle(any(), any(), any(), any());
    }

    @Test
    @DisplayName("StockReservationFailed 수신 시 환불(+amount) 처리 후 PaymentReverted 발행")
    void refunds_and_publishes_PaymentReverted() throws Exception {
        SagaEvents.StockReservationFailed event = SagaEvents.StockReservationFailed.builder()
                .eventId(UUID.randomUUID())
                .orderId(42L)
                .customerId(7L)
                .username("user")
                .amountToRefund(10000)
                .reason("재고 동시성 충돌")
                .build();

        consumer.onStockReservationFailed(objectMapper.writeValueAsString(event));

        ArgumentCaptor<ChangeBalanceDto.Input> formCaptor = ArgumentCaptor.forClass(ChangeBalanceDto.Input.class);
        verify(balanceService).changeBalance(eq(7L), formCaptor.capture());
        assertThat(formCaptor.getValue().getMoney()).isEqualTo(10000); // 양수 = 환불

        ArgumentCaptor<SagaEvents.PaymentReverted> captor = ArgumentCaptor.forClass(SagaEvents.PaymentReverted.class);
        verify(publisher).publish(eq(SagaTopics.PAYMENT_REVERTED), captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(42L);
    }
}
