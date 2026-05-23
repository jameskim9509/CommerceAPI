package com.zerobase.userApi.saga;

public final class SagaTopics {
    public static final String ORDER_CREATED = "order-created";
    public static final String PAYMENT_DEDUCTED = "payment-deducted";
    public static final String PAYMENT_FAILED = "payment-failed";
    public static final String STOCK_RESERVATION_FAILED = "stock-reservation-failed";
    public static final String PAYMENT_REVERTED = "payment-reverted";

    private SagaTopics() {}
}
