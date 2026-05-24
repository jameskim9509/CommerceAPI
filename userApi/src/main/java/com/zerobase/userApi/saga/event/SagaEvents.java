package com.zerobase.userApi.saga.event;

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
    public static class OrderCreated {
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
    public static class PaymentDeducted {
        private UUID eventId;
        private Long orderId;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentFailed {
        private UUID eventId;
        private Long orderId;
        private String reason;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockReservationFailed {
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
    public static class PaymentReverted {
        private UUID eventId;
        private Long orderId;
    }

    private SagaEvents() {}
}
