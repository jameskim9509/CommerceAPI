package com.zerobase.orderApi.service;

import com.zerobase.orderApi.domain.Product;
import com.zerobase.orderApi.domain.ProductItem;
import com.zerobase.orderApi.dto.AddProductForm;
import com.zerobase.orderApi.dto.AddProductItemForm;
import com.zerobase.orderApi.dto.UpdateProductForm;
import com.zerobase.orderApi.dto.UpdateProductItemForm;
import com.zerobase.orderApi.exception.CustomException;
import com.zerobase.orderApi.repository.ProductItemRepository;
import com.zerobase.orderApi.repository.ProductRepository;
import com.zerobase.orderApi.security.CustomUserDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ProductServiceTest {

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductItemRepository productItemRepository;
    @Autowired
    private ProductService productService;

    @DisplayName("Product 및 ProductItem 등록, 수정, 삭제 성공")
    @Test
    void productServiceTest()
    {
        //given
        Long sellerId = 1L;
        AddProductForm.Input productForm = AddProductForm.Input.builder()
                .name("james")
                .description("abcdef")
                .addProductItemForms(
                        List.of(
                                AddProductItemForm.Input.builder()
                                        .count(1)
                                        .price(10000)
                                        .name("chocolate")
                                        .build()
                        )
                )
                .build();

        //when
        productService.addProduct(sellerId, productForm);
        Product findProduct = productRepository.findBySellerId(sellerId).get();

        //then
        assertEquals(productForm.getName(), findProduct.getName());
        assertEquals(productForm.getDescription(), findProduct.getDescription());
        assertEquals(sellerId, findProduct.getSellerId());

        //given
        AddProductItemForm.Input itemForm = AddProductItemForm.Input.builder()
                .productId(findProduct.getId())
                .count(1)
                .price(10000)
                .name("snack")
                .build();

        //when
        productService.addProductItem(sellerId, itemForm);

        //then
        assertEquals(
                "chocolate", findProduct.getProductItemList().get(0).getName()
        );
        assertEquals(
                1, findProduct.getProductItemList().get(0).getCount()
        );
        assertEquals(
                10000, findProduct.getProductItemList().get(0).getPrice()
        );

        //given
        UpdateProductForm.Input updateProductForm = UpdateProductForm.Input.builder()
                .productId(findProduct.getId())
                .name("jameskim")
                .description("sweet snacks")
                .updateProductItemForms(
                        findProduct.getProductItemList().stream()
                                .map( it ->
                                        UpdateProductItemForm.Input.builder()
                                                .id(it.getId())
                                                .productId(findProduct.getId())
                                                .name("sweet chocolate")
                                                .price(5000)
                                                .count(1)
                                                .build()
                                ).collect(Collectors.toList())
                ).build();

        productService.updateProduct(sellerId, updateProductForm);

        // when
        Product updatedProduct = productRepository.findBySellerIdAndId(sellerId, findProduct.getId()).get();

        // then
        assertEquals( "sweet chocolate", updatedProduct.getProductItemList().get(0).getName());
        assertEquals("jameskim", updatedProduct.getName());

        // given
        UpdateProductItemForm.Input updateItemForm = UpdateProductItemForm.Input.builder()
                .id(updatedProduct.getProductItemList().get(0).getId())
                .productId(updatedProduct.getId())
                .name("sweetest chocolate")
                .price(10000)
                .count(1)
                .build();

        // when
        productService.updateProductItem(sellerId, updateItemForm);

        ProductItem updatedProductItem = productItemRepository.findBySellerIdAndId(
                sellerId, updateItemForm.getId()
        ).get();

        // then
        assertEquals("sweetest chocolate", updatedProductItem.getName());

        // given
        Long itemId = updatedProduct.getProductItemList().get(0).getId();

        // when
        productService.deleteProductItem(sellerId, itemId);

        // then
        assertNull(
                productItemRepository
                        .findBySellerIdAndId(sellerId, itemId)
                        .orElse(null)
        );

        // given
        Long productId = updatedProduct.getId();

        // when
        productService.deleteProduct(sellerId, productId);

        // then
        assertNull(
                productRepository
                        .findBySellerIdAndId(sellerId, productId)
                        .orElse(null)
        );
        assertNull(
                productItemRepository
                        .findBySellerIdAndId(sellerId, itemId)
                        .orElse(null)
        );
    }
}