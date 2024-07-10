package com.zerobase.orderApi.repository;

import com.zerobase.orderApi.domain.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    @EntityGraph(attributePaths = {"productItemList"})
    Optional<Product> findBySellerId(Long sellerId);

    @EntityGraph(attributePaths = {"productItemList"})
    Optional<Product> findBySellerIdAndId(Long sellerId, Long id);
}
