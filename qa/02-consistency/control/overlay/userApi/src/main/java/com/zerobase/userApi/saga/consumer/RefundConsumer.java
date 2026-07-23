package com.zerobase.userApi.saga.consumer;

import com.zerobase.userApi.saga.SagaTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// =============================================================================
// [ADR-008 control 무방어 오버레이] 원하는 장애 ③ 결제 후 재고 실패→환불 보상 제거.
//   이벤트는 소비(consumer lag 정리)하되 환불/PaymentReverted 를 하지 않는다.
//   → 차감된 잔액이 환불되지 않아 돈 보존 위반 + 해당 주문이 PENDING 으로 잔류.
//   빌드 중에만 원본 위에 덮어씀. 원본: userApi/.../saga/consumer/RefundConsumer.java
// =============================================================================
@Component
public class RefundConsumer {

    private static final String CONSUMER_NAME = "userapi-refund";

    @KafkaListener(topics = SagaTopics.STOCK_RESERVATION_FAILED, groupId = CONSUMER_NAME)
    public void onStockReservationFailed(String json) {
        // (control) 보상 없음 — 이벤트만 소비하고 드롭.
    }
}
