package com.zerobase.orderApi.dto;

import com.zerobase.orderApi.domain.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


public class AddProductForm {
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Builder
    public static class Input{
        private String name;
        private String description;
        private List<AddProductItemForm.Input> addProductItemForms;

        public Product toProductEntity(Long sellerId)
        {
            Product product =  Product.builder()
                                .sellerId(sellerId)
                                .name(this.name)
                                .description(this.description)
                                .productItemList(new ArrayList<>())
                                .build();

            if(addProductItemForms != null && addProductItemForms.size() > 0) {
                addProductItemForms.stream()
                        .forEach(it -> product.addProductItem(it.toProductItemEntity(sellerId)));
            }

            return product;
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Output{
        private Long sellerId;
        private String name;
        private String description;
        private List<AddProductItemForm.Output> addProductItemForms;

        public static AddProductForm.Output fromProductEntity(Product entity)
        {
            return Output.builder()
                    .sellerId(entity.getSellerId())
                    .name(entity.getName())
                    .description(entity.getDescription())
                    .addProductItemForms(
                            entity.getProductItemList().stream()
                                    .map(AddProductItemForm.Output::fromProductItemEntity)
                                    .collect(Collectors.toList())
                    ).build();
        }
    }
}
