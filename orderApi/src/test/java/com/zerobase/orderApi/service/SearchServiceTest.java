package com.zerobase.orderApi.service;

import com.zerobase.orderApi.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class SearchServiceTest {

    @Autowired
    private SearchService searchService;
    @Autowired
    private ProductService productService;

    private Long productId;

    @BeforeEach
    void init()
    {
        AddProductForm.Input form = AddProductForm.Input.builder()
                .name("james")
                .description("abcdef")
                .addProductItemForms(
                        List.of(
                                AddProductItemForm.Input.builder()
                                        .count(1)
                                        .price(5000)
                                        .name("chocolate")
                                        .build(),
                                AddProductItemForm.Input.builder()
                                        .count(1)
                                        .price(10000)
                                        .name("sweet chocolate")
                                        .build(),
                                AddProductItemForm.Input.builder()
                                        .count(1)
                                        .price(20000)
                                        .name("sweeter chocolate")
                                        .build(),
                                AddProductItemForm.Input.builder()
                                        .count(1)
                                        .price(50000)
                                        .name("sweetest chocolate")
                                        .build()
                        )
                )
                .build();

        AddProductForm.Output output = productService.addProduct(1L, form);
        productId = output.getProductId();
    }

    @DisplayName("이름으로 product 조회 성공")
    @Test
    void searchByProductNameTest()
    {
        List<ProductDto> productDtoList =
                searchService.searchByProductName("james");

        assertEquals("james", productDtoList.get(0).getName());
    }

    @DisplayName("productId로 product 조회 성공")
    @Test
    void getByProductIdTest()
    {
        ProductDto productDto =
                searchService.getByProductId(productId);

        assertEquals("james", productDto.getName());
        assertEquals(4, productDto.getProductItemList().size());
    }

    @DisplayName("조건으로 아이템 조회 성공")
    @Test
    void searchProductItems()
    {
        Pageable pageable = PageRequest.of(0, 10);
        ProductItemSearchCondDto form =
                ProductItemSearchCondDto.builder()
                .itemName("chocolate")
                .priceMin(10000)
                .priceMax(20000)
                .build();

        List<ProductItemDto> productItemDtoList =
                searchService.searchProductItems(form, pageable);

        assertEquals(2, productItemDtoList.size());
    }
}
