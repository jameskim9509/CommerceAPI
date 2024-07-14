package com.zerobase.orderApi.service;

import com.zerobase.orderApi.domain.Cart;
import com.zerobase.orderApi.domain.Product;
import com.zerobase.orderApi.domain.ProductItem;
import com.zerobase.orderApi.dto.AddProductCartForm;
import com.zerobase.orderApi.dto.ProductItemDto;
import com.zerobase.orderApi.repository.ProductRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class CartServiceTest {

    @Mock
    private RedisClientService redisClientService;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CartService cartService;

    @BeforeEach
    void init()
    {
        List<Cart.ProductItem> cartProductItemList = new ArrayList<>(
                List.of(
                    Cart.ProductItem.builder()
                            .id(1L)
                            .name("chocolate")
                            .count(1)
                            .price(5000)
                            .build()
                )
        );

        List<Cart.Product> cartProductList = new ArrayList<>(
                List.of(
                    Cart.Product.builder()
                            .id(1L)
                            .sellerId(1L)
                            .name("james")
                            .description("snacks")
                            .productItemList(cartProductItemList)
                            .build()
                )
        );

        cartProductList.add(null);

        given(redisClientService.get(anyLong(), any()))
                .willReturn(
                        Cart.builder()
                            .customerId(1L)
                            .productList(cartProductList)
                            .messages(new ArrayList<>())
                            .build()
                );

        List<ProductItem> productItemList = new ArrayList<>(
                List.of(
                    ProductItem.builder()
                            .Id(1L)
                            .sellerId(1L)
                            .name("chocolate")
                            .count(5)
                            .price(5000)
                            .build(),
                    ProductItem.builder()
                            .Id(2L)
                            .sellerId(1L)
                            .name("sweet chocolate")
                            .count(5)
                            .price(10000)
                            .build(),
                    ProductItem.builder()
                            .Id(3L)
                            .sellerId(1L)
                            .name("sweeter chocolate")
                            .count(5)
                            .price(20000)
                            .build(),
                    ProductItem.builder()
                            .Id(4L)
                            .sellerId(1L)
                            .name("sweetest chocolate")
                            .count(5)
                            .price(50000)
                            .build()
                )
        );
        Product product = Product.builder()
                .id(1L)
                .sellerId(1L)
                .name("james")
                .description("snacks")
                .productItemList(new ArrayList<>())
                .build();

        productItemList.stream()
                .forEach(it -> product.addProductItem(it));

        given(productRepository.findById(anyLong()))
                .willReturn(Optional.of(product));
    }

    @DisplayName("Cart 추가 성공")
    @Test
    void addCart()
    {
        //given
        AddProductCartForm form = AddProductCartForm.builder()
                .id(1L)
                .sellerId(1L)
                .name("james")
                .description("snacks")
                .productItemList(
                        new ArrayList<>(
                                List.of(
                                    ProductItemDto.builder()
                                            .name("chocolate")
                                            .id(1L)
                                            .price(5000)
                                            .count(2)
                                            .build(),
                                    ProductItemDto.builder()
                                            .name("sweet chocolate")
                                            .id(2L)
                                            .price(10000)
                                            .count(1)
                                            .build()
                                )
                        )
                ).build();

        ArgumentCaptor<Cart> cartArgumentCaptor = ArgumentCaptor.forClass(Cart.class);

        cartService.addCart(1L, form);

        verify(redisClientService, times(1)).put(anyLong(), cartArgumentCaptor.capture());
        Cart capturedCart = cartArgumentCaptor.getValue();

        Assertions.assertTrue(
                capturedCart.getProductList().get(0)
                    .getProductItemList().stream()
                        .anyMatch(it -> "chocolate".equals(it.getName()) && it.getCount().equals(3))
        );
        Assertions.assertTrue(
                capturedCart.getProductList().get(0)
                    .getProductItemList().stream()
                        .anyMatch(it -> "sweet chocolate".equals(it.getName()) && it.getCount().equals(1))
        );
    }
}
