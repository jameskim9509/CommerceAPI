package com.zerobase.orderApi.dto;

import com.zerobase.orderApi.domain.ProductItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


public class UpdateProductItemForm {

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Builder
    public static class Input{
        private Long id;
        private Long productId;
        private String name;
        private Integer price;
        private Integer count;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Builder
    public static class Output{
        private Long id;
        private Long sellerId;
        private String name;
        private Integer price;
        private Integer count;

        public static UpdateProductItemForm.Output fromProductItemEntity(
                ProductItem entity
        )
        {
            return Output.builder()
                    .id(entity.getId())
                    .sellerId(entity.getSellerId())
                    .name(entity.getName())
                    .price(entity.getPrice())
                    .count(entity.getCount())
                    .build();
        }
    }
}
