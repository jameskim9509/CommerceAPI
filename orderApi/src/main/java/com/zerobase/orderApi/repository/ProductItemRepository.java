package com.zerobase.orderApi.repository;

import com.zerobase.orderApi.domain.ProductItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductItemRepository extends JpaRepository<ProductItem, Long>, ProductItemRepositoryCustom {
    Optional<ProductItem> findBySellerIdAndId(Long sellerId, Long id);
}
