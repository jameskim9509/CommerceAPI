package com.zerobase.orderApi.service;

import org.springframework.data.domain.Pageable;
import com.zerobase.orderApi.dto.ProductDto;
import com.zerobase.orderApi.dto.ProductItemDto;
import com.zerobase.orderApi.dto.ProductItemSearchCondDto;
import com.zerobase.orderApi.exception.CustomException;
import com.zerobase.orderApi.exception.ErrorCode;
import com.zerobase.orderApi.repository.ProductItemRepository;
import com.zerobase.orderApi.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final ProductRepository productRepository;
    private final ProductItemRepository productItemRepository;

    public List<ProductDto> searchByProductName(String productName)
    {
        return productRepository.searchByName(productName).stream()
                .map(ProductDto::fromWithoutItems)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProductDto getByProductId(Long productId)
    {
        return ProductDto.from(
                productRepository.findById(productId)
                        .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND))
        );
    }

    @Transactional
    public List<ProductDto> getListByProductId(List<Long> productIdList)
    {
        return productRepository.findAllById(productIdList).stream()
                .map(ProductDto::from)
                .collect(Collectors.toList());
    }

    public List<ProductItemDto> searchProductItems(ProductItemSearchCondDto form, Pageable pageable)
    {
        return productItemRepository.searchByCond(
                form.getItemName(), form.getPriceMin(), form.getPriceMax(), pageable
        ).stream().map(ProductItemDto::from).collect(Collectors.toList());
    }
}
