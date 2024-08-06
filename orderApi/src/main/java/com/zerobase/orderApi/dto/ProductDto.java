package com.zerobase.orderApi.dto;

import com.zerobase.orderApi.domain.Product;
import com.zerobase.orderApi.domain.ProductItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductDto {
    private Long id;
    private String name;
    private String description;
    private List<ProductItemDto> productItemList;

    public static ProductDto from(Product product)
    {
        List<ProductItemDto> productItemDtoList = product.getProductItemList()
                .stream().map(ProductItemDto::from)
                .collect(Collectors.toList());

        return ProductDto.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .productItemList(productItemDtoList)
                .build();
    }

    public static ProductDto fromWithoutItems(Product product)
    {
        return ProductDto.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .build();
    }
}
