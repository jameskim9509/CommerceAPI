package com.zerobase.orderApi.service;

import com.zerobase.orderApi.domain.Product;
import com.zerobase.orderApi.domain.ProductItem;
import com.zerobase.orderApi.dto.AddProductForm;
import com.zerobase.orderApi.dto.AddProductItemForm;
import com.zerobase.orderApi.exception.CustomException;
import com.zerobase.orderApi.exception.ErrorCode;
import com.zerobase.orderApi.repository.ProductItemRepository;
import com.zerobase.orderApi.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductItemRepository productItemRepository;

    public AddProductForm.Output addProduct(
            Long sellerId, AddProductForm.Input form
    )
    {
        // cascade에 의해 productItem도 같이 저장된다.
        Product savedProduct =
                productRepository.save(form.toProductEntity(sellerId));

        return AddProductForm.Output.fromProductEntity(savedProduct);
    }

    @Transactional
    public AddProductForm.Output addProductItem(
            Long sellerId, AddProductItemForm.Input form
    )
    {
        Product product = productRepository.findBySellerIdAndId(sellerId, form.getProductId())
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        if( product.getProductItemList().stream()
                .anyMatch(it -> it.getName().equals(form.getName())))
            throw new CustomException(ErrorCode.PRODUCT_ITEM_EXIST);

        ProductItem productItem = form.toProductItemEntity(sellerId);
        product.addProductItem(productItem);

        return AddProductForm.Output.fromProductEntity(product);
    }
}
