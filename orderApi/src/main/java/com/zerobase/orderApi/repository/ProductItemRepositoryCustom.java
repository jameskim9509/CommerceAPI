package com.zerobase.orderApi.repository;

import com.zerobase.orderApi.domain.ProductItem;

import org.springframework.data.domain.Pageable;
import java.util.List;

public interface ProductItemRepositoryCustom {
    List<ProductItem> searchByCond(
            String itemName, Integer priceMin, Integer priceMax, Pageable pageable
    );
}
