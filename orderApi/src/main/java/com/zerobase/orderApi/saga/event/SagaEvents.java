package com.zerobase.orderApi.saga.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

public class SagaEvents {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderCreated implements SagaEvent {
        private UUID eventId;
        private Long orderId;
        private Long customerId;
        private String username;
        private Integer totalPrice;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentDeducted implements SagaEvent {
        private UUID eventId;
        private Long orderId;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentFailed implements SagaEvent {
        private UUID eventId;
        private Long orderId;
        private String reason;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockReservationFailed implements SagaEvent {
        private UUID eventId;
        private Long orderId;
        private Long customerId;
        private String username;
        private Integer amountToRefund;
        private String reason;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentReverted implements SagaEvent {
        private UUID eventId;
        private Long orderId;
    }

    private SagaEvents() {}
}
