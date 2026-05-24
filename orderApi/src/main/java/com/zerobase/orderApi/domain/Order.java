package com.zerobase.orderApi.domain;

import com.zerobase.orderApi.exception.CustomException;
import com.zerobase.orderApi.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long customerId;
    private String username;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private Integer totalPrice;

    @Column(length = 500)
    private String failureReason;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "order_id")
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    public void markPaid() {
        if (status == OrderStatus.PAID) return;
        if (status != OrderStatus.PENDING) {
            throw new CustomException(ErrorCode.INVALID_ORDER_STATE_TRANSITION);
        }
        this.status = OrderStatus.PAID;
    }

    public void markConfirmed() {
        if (status == OrderStatus.CONFIRMED) return;
        if (status != OrderStatus.PAID) {
            throw new CustomException(ErrorCode.INVALID_ORDER_STATE_TRANSITION);
        }
        this.status = OrderStatus.CONFIRMED;
    }

    public void markFailed(String reason) {
        if (status == OrderStatus.FAILED) return;
        if (status == OrderStatus.CONFIRMED) {
            throw new CustomException(ErrorCode.INVALID_ORDER_STATE_TRANSITION);
        }
        this.status = OrderStatus.FAILED;
        this.failureReason = reason;
    }
}
