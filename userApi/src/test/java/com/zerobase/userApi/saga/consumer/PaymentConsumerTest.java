package com.zerobase.userApi.saga.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobase.userApi.dto.ChangeBalanceDto;
import com.zerobase.userApi.exception.CustomException;
import com.zerobase.userApi.exception.ErrorCode;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentConsumerTest {

    @Mock private IdempotentEventHandler idempotentHandler;
    @Mock private CustomerBalanceHistoryService balanceService;
    @Mock private SagaEventPublisher publisher;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PaymentConsumer consumer;

    @BeforeEach
    void init() {
        consumer = new PaymentConsumer(idempotentHandler, balanceService, publisher, objectMapper);
        // IdempotentEventHandler 를 통과시켜 processor 가 항상 실행되게 stub
        doAnswer(invocation -> {
            Consumer<Object> processor = invocation.getArgument(3);
            processor.accept(invocation.getArgument(2));
            return null;
        }).when(idempotentHandler).handle(any(), any(), any(), any());
    }

    @Test
    @DisplayName("정상 결제: balance 차감 + PaymentDeducted 발행")
    void publishes_PaymentDeducted_on_success() throws Exception {
        SagaEvents.OrderCreated event = SagaEvents.OrderCreated.builder()
                .eventId(UUID.randomUUID())
                .orderId(42L)
                .customerId(7L)
                .username("user")
                .totalPrice(10000)
                .build();

        consumer.onOrderCreated(objectMapper.writeValueAsString(event));

        ArgumentCaptor<ChangeBalanceDto.Input> formCaptor = ArgumentCaptor.forClass(ChangeBalanceDto.Input.class);
        verify(balanceService).changeBalance(eq(7L), formCaptor.capture());
        assertThat(formCaptor.getValue().getMoney()).isEqualTo(-10000);

        verify(publisher).publish(eq(SagaTopics.PAYMENT_DEDUCTED), any(SagaEvents.PaymentDeducted.class));
        verify(publisher, never()).publish(eq(SagaTopics.PAYMENT_FAILED), any());
    }

    @Test
    @DisplayName("잔액 부족: PaymentFailed 발행하고 PaymentDeducted 는 발행하지 않음")
    void publishes_PaymentFailed_on_insufficient_balance() throws Exception {
        given(balanceService.changeBalance(any(), any()))
                .willThrow(new CustomException(ErrorCode.NOT_ENOUGH_BALANCE));

        SagaEvents.OrderCreated event = SagaEvents.OrderCreated.builder()
                .eventId(UUID.randomUUID())
                .orderId(42L)
                .customerId(7L)
                .username("user")
                .totalPrice(10000)
                .build();

        consumer.onOrderCreated(objectMapper.writeValueAsString(event));

        ArgumentCaptor<SagaEvents.PaymentFailed> captor = ArgumentCaptor.forClass(SagaEvents.PaymentFailed.class);
        verify(publisher).publish(eq(SagaTopics.PAYMENT_FAILED), captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(42L);

        verify(publisher, never()).publish(eq(SagaTopics.PAYMENT_DEDUCTED), any());
    }
}
