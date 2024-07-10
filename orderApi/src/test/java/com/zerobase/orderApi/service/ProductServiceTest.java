package com.zerobase.orderApi.service;

import com.zerobase.orderApi.domain.Product;
import com.zerobase.orderApi.dto.AddProductForm;
import com.zerobase.orderApi.dto.AddProductItemForm;
import com.zerobase.orderApi.dto.UpdateProductForm;
import com.zerobase.orderApi.dto.UpdateProductItemForm;
import com.zerobase.orderApi.repository.ProductItemRepository;
import com.zerobase.orderApi.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ProductServiceTest {

    @Autowired
    private ProductRepository productRepository;
    private ProductItemRepository productItemRepository;
    @Autowired
    private ProductService productService;

    @Transactional
    @DisplayName("Product 등록 및 ProductItem 추가 후 수정 성공")
    @Test
    void productServiceTest()
    {
        //given
        Long sellerId = 1L;
        AddProductForm.Input productForm = AddProductForm.Input.builder()
                .name("james")
                .description("abcdef")
                .addProductItemForms(null)
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
                .name("chocolate")
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

        //when
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

        // then
        assertEquals("sweetest chocolate", updatedProduct.getProductItemList().get(0).getName());
    }
}