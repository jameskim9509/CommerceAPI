package com.zerobase.orderApi.domain;

import com.zerobase.orderApi.exception.CustomException;
import com.zerobase.orderApi.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    @DisplayName("markPaid: PENDING -> PAID")
    void markPaid_from_pending() {
        Order order = newOrder(OrderStatus.PENDING);
        order.markPaid();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("markPaid: 이미 PAID 면 멱등 (no-op)")
    void markPaid_idempotent_when_already_paid() {
        Order order = newOrder(OrderStatus.PAID);
        order.markPaid();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("markPaid: CONFIRMED 에서 시도하면 INVALID_ORDER_STATE_TRANSITION")
    void markPaid_from_confirmed_throws() {
        Order order = newOrder(OrderStatus.CONFIRMED);
        assertThatThrownBy(order::markPaid)
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ORDER_STATE_TRANSITION);
    }

    @Test
    @DisplayName("markConfirmed: PAID -> CONFIRMED")
    void markConfirmed_from_paid() {
        Order order = newOrder(OrderStatus.PAID);
        order.markConfirmed();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("markConfirmed: PENDING 에서 시도하면 INVALID_ORDER_STATE_TRANSITION")
    void markConfirmed_from_pending_throws() {
        Order order = newOrder(OrderStatus.PENDING);
        assertThatThrownBy(order::markConfirmed)
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ORDER_STATE_TRANSITION);
    }

    @Test
    @DisplayName("markFailed: PENDING -> FAILED + 사유 저장")
    void markFailed_from_pending() {
        Order order = newOrder(OrderStatus.PENDING);
        order.markFailed("결제 실패");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(order.getFailureReason()).isEqualTo("결제 실패");
    }

    @Test
    @DisplayName("markFailed: PAID 에서도 FAILED 로 전이 가능 (재고 충돌 → 환불 흐름)")
    void markFailed_from_paid() {
        Order order = newOrder(OrderStatus.PAID);
        order.markFailed("환불 완료");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
    }

    @Test
    @DisplayName("markFailed: 이미 FAILED 면 멱등")
    void markFailed_idempotent_when_already_failed() {
        Order order = newOrder(OrderStatus.FAILED);
        order.markFailed("다시 실패");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
    }

    @Test
    @DisplayName("markFailed: CONFIRMED 는 되돌릴 수 없음 (불변)")
    void markFailed_from_confirmed_throws() {
        Order order = newOrder(OrderStatus.CONFIRMED);
        assertThatThrownBy(() -> order.markFailed("뒤늦은 실패"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ORDER_STATE_TRANSITION);
    }

    private Order newOrder(OrderStatus status) {
        return Order.builder()
                .customerId(1L)
                .username("u")
                .status(status)
                .totalPrice(10000)
                .build();
    }
}
