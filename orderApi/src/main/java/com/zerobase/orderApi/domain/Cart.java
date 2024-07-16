package com.zerobase.orderApi.domain;

import jakarta.persistence.Id;
import lombok.*;
import org.springframework.data.redis.core.RedisHash;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
//@RedisHash("cart")
public class Cart {
    @Id
    private Long customerId;
    private List<Product> productList;
    private List<String> messages;

    public void addMessage(String message)
    {
        messages.add(message);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
        public static class Product{
        private Long id;
        private Long sellerId;
        private String name;
        private String description;
        private List<ProductItem> productItemList;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductItem{
        private Long id;
        private String name;
        private Integer count;
        private Integer price;
    }

    public Cart clone()
    {
        return Cart.builder()
                .customerId(this.customerId)
                .productList(new ArrayList<>(this.productList))
                .messages(new ArrayList<>(this.messages))
                .build();
    }
}
