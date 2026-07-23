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
        // [control/no-defense] ③ 재고 실패→환불 보상 제거 — 이벤트만 소비(lag 정리)하고 환불/PaymentReverted 안 함.
        //   → 차감된 잔액 미환불(돈 보존 위반) + 해당 주문 PENDING 잔류.
    }
}
