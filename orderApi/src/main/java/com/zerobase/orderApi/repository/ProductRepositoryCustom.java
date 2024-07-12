package com.zerobase.orderApi.repository;

import com.zerobase.orderApi.domain.Product;

import java.util.List;

public interface ProductRepositoryCustom {
    List<Product> searchByName(String productName);
}
