package com.zerobase.orderApi.repository;

import com.zerobase.orderApi.domain.ProductItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductItemRepository extends JpaRepository<ProductItem, Long> {
}
