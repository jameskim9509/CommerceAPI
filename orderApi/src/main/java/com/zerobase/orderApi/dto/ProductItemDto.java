package com.zerobase.orderApi.dto;

import com.zerobase.orderApi.domain.Cart;
import com.zerobase.orderApi.domain.ProductItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductItemDto {
    private Long id;
    private String name;
    private Integer price;
    private Integer count;

    public static ProductItemDto from(ProductItem item)
    {
        return ProductItemDto.builder()
                .id(item.getId())
                .name(item.getName())
                .price(item.getPrice())
                .count(item.getCount())
                .build();
    }

    public static Cart.ProductItem toProductItemRedisEntity(
            ProductItemDto productItemDto
    )
    {
        return Cart.ProductItem.builder()
                .id(productItemDto.getId())
                .name(productItemDto.getName())
                .price(productItemDto.getPrice())
                .count(productItemDto.getCount())
                .build();
    }
}
