package com.zerobase.orderApi.dto;

import com.zerobase.orderApi.domain.Cart;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddProductCartForm {
    private Long id;
    private Long sellerId;
    private String name;
    private String description;
    private List<ProductItemDto> productItemList;

    public Cart.Product toProductRedisEntity()
    {
        return Cart.Product.builder()
                .id(this.id)
                .sellerId(this.sellerId)
                .name(this.name)
                .description(this.description)
                .productItemList(
                        this.getProductItemList().stream()
                                .map(ProductItemDto::toProductItemRedisEntity)
                                .collect(Collectors.toList())
                )
                .build();
    }
}
