package com.zerobase.orderApi.dto;

import com.zerobase.orderApi.domain.Order;
import com.zerobase.orderApi.domain.OrderStatus;
import lombok.*;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDto {

    private Long orderId;
    private OrderStatus status;
    private Integer totalPrice;
    private String failureReason;
    private List<Item> items;

    public static OrderDto from(Order order) {
        return OrderDto.builder()
                .orderId(order.getId())
                .status(order.getStatus())
                .totalPrice(order.getTotalPrice())
                .failureReason(order.getFailureReason())
                .items(order.getItems().stream()
                        .map(it -> Item.builder()
                                .productItemId(it.getProductItemId())
                                .name(it.getName())
                                .count(it.getCount())
                                .price(it.getPrice())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long productItemId;
        private String name;
        private Integer count;
        private Integer price;
    }
}
