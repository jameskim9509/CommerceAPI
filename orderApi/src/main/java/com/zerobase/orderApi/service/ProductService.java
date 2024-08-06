package com.zerobase.orderApi.service;

import com.zerobase.orderApi.domain.Product;
import com.zerobase.orderApi.domain.ProductItem;
import com.zerobase.orderApi.dto.AddProductForm;
import com.zerobase.orderApi.dto.AddProductItemForm;
import com.zerobase.orderApi.dto.UpdateProductForm;
import com.zerobase.orderApi.dto.UpdateProductItemForm;
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

        ProductItem productItem = productItemRepository.save(
                form.toProductItemEntity(sellerId)
        );

        product.addProductItem(productItem);

        return AddProductForm.Output.fromProductEntity(product);
    }

    @Transactional
    public UpdateProductItemForm.Output updateProductItem(Long sellerId, UpdateProductItemForm.Input form)
    {
        ProductItem productItem =
                productItemRepository.findBySellerIdAndId(sellerId, form.getId())
                    .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_ITEM_NOT_FOUND));

        productItem.setPrice(form.getPrice());
        productItem.setCount(form.getCount());
        productItem.setName(form.getName());

        return UpdateProductItemForm.Output.fromProductItemEntity(productItem);
    }

    @Transactional
    public UpdateProductForm.Output updateProduct(Long sellerId, UpdateProductForm.Input form)
    {
        Product product =
                productRepository.findBySellerIdAndId(sellerId, form.getProductId())
                        .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        product.setName(form.getName());
        product.setDescription(form.getDescription());

        form.getUpdateProductItemForms().stream()
                .forEach(it -> updateProductItem(sellerId, it));

        return UpdateProductForm.Output.fromProductEntity(product);
    }

    public String deleteProductItem(Long sellerId, Long id)
    {
        ProductItem productItem =
                productItemRepository.findBySellerIdAndId(sellerId, id)
                        .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_ITEM_NOT_FOUND));

        productItemRepository.delete(productItem);

        return "정상적으로 아이템이 제거되었습니다.";
    }

    public String deleteProduct(Long sellerId, Long id)
    {
        Product product =
                productRepository.findBySellerIdAndId(sellerId, id)
                        .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        productRepository.delete(product);

        return "정상적으로 상품이 제거되었습니다.";
    }
}
