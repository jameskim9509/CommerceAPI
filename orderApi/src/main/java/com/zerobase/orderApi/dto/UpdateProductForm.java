package com.zerobase.orderApi.dto;

import com.zerobase.orderApi.domain.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

public class UpdateProductForm {

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Builder
    public static class Input{
        private Long productId;
        private String name;
        private String description;
        private List<UpdateProductItemForm.Input> updateProductItemForms;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Builder
    public static class Output{
        private Long id;

        private Long sellerId;
        private String name;
        private String description;

        private List<UpdateProductItemForm.Output> updatedProductItemList;

        public static UpdateProductForm.Output fromProductEntity(Product entity)
        {
            return Output.builder()
                    .id(entity.getId())
                    .sellerId(entity.getSellerId())
                    .name(entity.getName())
                    .description(entity.getDescription())
                    .updatedProductItemList(
                            entity.getProductItemList().stream()
                                    .map(UpdateProductItemForm.Output::fromProductItemEntity)
                                    .collect(Collectors.toList())
                    ).build();
        }
    }
}
