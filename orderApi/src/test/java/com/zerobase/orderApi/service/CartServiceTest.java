package com.zerobase.orderApi.service;

import com.zerobase.orderApi.domain.Cart;
import com.zerobase.orderApi.domain.Product;
import com.zerobase.orderApi.domain.ProductItem;
import com.zerobase.orderApi.dto.AddProductCartForm;
import com.zerobase.orderApi.dto.ProductItemDto;
import com.zerobase.orderApi.repository.ProductItemRepository;
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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CartServiceTest {

    @Mock
    private RedisClientService redisClientService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductItemRepository productItemRepository;

    @InjectMocks
    private CartService cartService;

    @Mock
    private UserClient userClient;

    private OrderService orderService;

    @BeforeEach
    void init()
    {
        orderService =
                new OrderService(redisClientService, productItemRepository, cartService, userClient);

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

    @DisplayName("장바구니 추가 성공")
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
                                            .build(),
                                    ProductItemDto.builder()
                                            .name("sweeter chocolate")
                                            .id(3L)
                                            .price(20000)
                                            .count(3)
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
        Assertions.assertTrue(
                capturedCart.getProductList().get(0)
                        .getProductItemList().stream()
                        .anyMatch(it -> "sweeter chocolate".equals(it.getName()) && it.getCount().equals(3))
        );
    }

    @DisplayName("장바구니 조회 성공")
    @Test
    void getCart()
    {
        addCart();

        // given
        // repository 수정
        ProductItem item1 = ProductItem.builder()
                .Id(1L)
                .sellerId(1L)
                .name("chocolate")
                // 5 -> 2
                .count(2)
                .price(5000)
                .build();

        ProductItem item2 = ProductItem.builder()
                .Id(2L)
                .sellerId(1L)
                // "sweet chocolate" -> "sweet choco"
                .name("sweet choco")
                .count(5)
                // 10000 -> 15000
                .price(15000)
                .build();

        ProductItem item4 = ProductItem.builder()
                .Id(4L)
                .sellerId(1L)
                .name("sweetest chocolate")
                .count(5)
                .price(50000)
                .build();

        List<ProductItem> productItemList = new ArrayList<>(
                List.of(item1, item2, item4)// 3 번 아이템 제거
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

        given(productItemRepository.findById(1L))
                .willReturn(Optional.of(item1));
        given(productItemRepository.findById(2L))
                .willReturn(Optional.of(item2));
        given(productItemRepository.findById(3L))
                .willReturn(Optional.empty());

        // 조회 기능 수행
        Cart cart = cartService.getCart(1L);

        // cart 메시지 검증
        Assertions.assertEquals(4, cart.getMessages().size());
        Assertions.assertTrue(
                cart.getMessages().stream()
                        .anyMatch(m -> m.contains("아이템 명"))
        );
        Assertions.assertTrue(
                cart.getMessages().stream()
                        .anyMatch(m -> m.contains("수량"))
        );
        Assertions.assertTrue(
                cart.getMessages().stream()
                        .anyMatch(m -> m.contains("가격"))
        );
        Assertions.assertTrue(
                cart.getMessages().stream()
                        .anyMatch(m -> m.contains("제거"))
        );
    }

    @DisplayName("장바구니 수정 성공")
    @Test
    void updateCart()
    {
        addCart();

        // given
        ProductItem item1 = ProductItem.builder()
                .Id(1L)
                .sellerId(1L)
                .name("chocolate")
                .count(5)
                .price(5000)
                .build();

        ProductItem item2 = ProductItem.builder()
                .Id(2L)
                .sellerId(1L)
                .name("sweet chocolate")
                .count(5)
                .price(10000)
                .build();

        given(productItemRepository.findById(1L))
                .willReturn(Optional.of(item1));
        given(productItemRepository.findById(2L))
                .willReturn(Optional.of(item2));

        Cart.Product productForm = Cart.Product.builder()
                .id(1L)
                .sellerId(1L)
                .name("james")
                .description("snacks")
                .productItemList(
                        new ArrayList<>(
                                List.of(
                                        Cart.ProductItem.builder()
                                                .name("chocolate")
                                                .id(1L)
                                                .price(5000)
                                                // 2 -> 1
                                                .count(1)
                                                .build(),
                                        Cart.ProductItem.builder()
                                                .name("sweet chocolate")
                                                .id(2L)
                                                .price(10000)
                                                .count(1)
                                                .build()
                                        // 3번 아이템 제거
                                )
                        )
                ).build();

        Cart cartForm = Cart.builder()
                    .customerId(1L)
                    .productList(new ArrayList<>(
                            List.of(productForm)
                    ))
                    .messages(new ArrayList<>())
                    .build();

        Cart cart = cartService.updateCart(1L, cartForm);

        Assertions.assertTrue(cart.getProductList().get(0)
                .getProductItemList().stream()
                .anyMatch(it -> it.getName().equals("chocolate") && it.getCount().equals(1)));

        Assertions.assertTrue(cart.getProductList().get(0)
                .getProductItemList().stream()
                .noneMatch(it -> it.getName().equals("sweeter chocolate")));
    }

    @DisplayName("장바구니 주문 성공")
    @Test
    void orderCart()
    {
        addCart();

        // given
        ProductItem item1 = ProductItem.builder()
                .Id(1L)
                .sellerId(1L)
                .name("chocolate")
                .count(5)
                .price(5000)
                .build();

        ProductItem item2 = ProductItem.builder()
                .Id(2L)
                .sellerId(1L)
                .name("sweet chocolate")
                .count(5)
                .price(10000)
                .build();

        ProductItem item3 = ProductItem.builder()
                .Id(3L)
                .sellerId(1L)
                .name("sweeter chocolate")
                .count(5)
                .price(20000)
                .build();

        given(productItemRepository.findById(1L))
                .willReturn(Optional.of(item1));
        given(productItemRepository.findById(2L))
                .willReturn(Optional.of(item2));
        given(productItemRepository.findById(3L))
                .willReturn(Optional.of(item3));

        given(userClient.changeBalance(anyString(), any()))
                .willReturn(new ResponseEntity<>(null, HttpStatusCode.valueOf(200)));

        Cart.Product productForm = Cart.Product.builder()
                .id(1L)
                .sellerId(1L)
                .name("james")
                .description("snacks")
                .productItemList(
                        new ArrayList<>(
                                List.of(
                                        Cart.ProductItem.builder()
                                                .name("chocolate")
                                                .id(1L)
                                                .price(5000)
                                                // 1개(of 3개)만 구매
                                                .count(1)
                                                .build(),
                                        Cart.ProductItem.builder()
                                                .name("sweet chocolate")
                                                .id(2L)
                                                .price(10000)
                                                .count(1)
                                                .build()
                                        // sweeter chocolate 주문 x
                                )
                        )
                ).build();

        Cart orderCart = Cart.builder()
                .customerId(1L)
                .productList(new ArrayList<>(List.of(productForm)))
                .messages(new ArrayList<>())
                .build();

        ArgumentCaptor<Cart> cartArgumentCaptor = ArgumentCaptor.forClass(Cart.class);

        // when
        orderService.order(
                "abcdefg", 1L, "james@naver.com", orderCart
        );

        verify(redisClientService, times(2)).put(anyLong(), cartArgumentCaptor.capture());
        Cart capturedCart = cartArgumentCaptor.getAllValues().get(1);
        Assertions.assertTrue(
                capturedCart.getProductList().get(0)
                        .getProductItemList().stream()
                        .anyMatch(it -> it.getName().equals("chocolate") && it.getCount().equals(2))
        );
        Assertions.assertTrue(
                capturedCart.getProductList().get(0)
                        .getProductItemList().stream()
                        .noneMatch(it -> it.getName().equals("sweet chocolate"))
        );
        Assertions.assertTrue(
                capturedCart.getProductList().get(0)
                        .getProductItemList().stream()
                        .anyMatch(it -> it.getName().equals("sweeter chocolate") && it.getCount().equals(3))
        );
        Assertions.assertEquals(4, productItemRepository.findById(1L).get().getCount());
        Assertions.assertEquals(4, productItemRepository.findById(2L).get().getCount());
        Assertions.assertEquals(5, productItemRepository.findById(3L).get().getCount());
    }
}
