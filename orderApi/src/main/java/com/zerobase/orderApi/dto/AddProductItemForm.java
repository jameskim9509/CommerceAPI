package com.zerobase.orderApi.dto;

import com.zerobase.orderApi.domain.Product;
import com.zerobase.orderApi.domain.ProductItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class AddProductItemForm {

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Builder
    public static class Input{
        private Long productId;
        private String name;
        private Integer price;
        private Integer count;

        public ProductItem toProductItemEntity(Long sellerId)
        {
            return ProductItem.builder()
                    .count(this.count)
                    .name(this.name)
                    .price(this.price)
                    .sellerId(sellerId)
                    .build();
        }
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Builder
    public static class Output{
        private Long id;
        private String name;
        private Integer price;
        private Integer count;

        public static AddProductItemForm.Output fromProductItemEntity(ProductItem item)
        {
            return AddProductItemForm.Output.builder()
                    .id(item.getId())
                    .name(item.getName())
                    .price(item.getPrice())
                    .count(item.getCount())
                    .build();
        }
    }
}
