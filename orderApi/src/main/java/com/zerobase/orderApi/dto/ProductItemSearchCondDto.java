package com.zerobase.orderApi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductItemSearchCondDto {
    private String itemName;
    private Integer priceMin;
    private Integer priceMax;
}
