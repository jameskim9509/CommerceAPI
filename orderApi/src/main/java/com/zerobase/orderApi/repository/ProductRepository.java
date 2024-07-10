package com.zerobase.orderApi.repository;

import com.zerobase.orderApi.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySellerId(Long sellerId);
    Optional<Product> findBySellerIdAndId(Long sellerId, Long id);
}
