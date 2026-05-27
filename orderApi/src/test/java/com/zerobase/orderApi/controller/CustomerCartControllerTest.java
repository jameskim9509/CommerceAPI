package com.zerobase.orderApi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobase.orderApi.domain.Cart;
import com.zerobase.orderApi.domain.Order;
import com.zerobase.orderApi.domain.OrderStatus;
import com.zerobase.orderApi.dto.AddProductCartForm;
import com.zerobase.orderApi.dto.OrderDto;
import com.zerobase.orderApi.dto.ProductItemDto;
import com.zerobase.orderApi.idempotency.IdempotencyService;
import com.zerobase.orderApi.security.CustomUserDetails;
import com.zerobase.orderApi.service.CartService;
import com.zerobase.orderApi.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith({MockitoExtension.class, RestDocumentationExtension.class})
class CustomerCartControllerTest {

    @Mock
    private CartService cartService;

    @Mock
    private OrderService orderService;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private CustomerCartController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .apply(documentationConfiguration(restDocumentation))
                .build();

        CustomUserDetails principal =
                new CustomUserDetails("customer@test.com", 1L, List.of("ROLE_CUSTOMER"));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Cart sampleCart() {
        return Cart.builder()
                .customerId(1L)
                .productList(new ArrayList<>())
                .messages(new ArrayList<>())
                .build();
    }

    @DisplayName("POST /customer/cart - 장바구니에 상품 추가")
    @Test
    void addCart() throws Exception {
        AddProductCartForm form = AddProductCartForm.builder()
                .id(10L)
                .sellerId(99L)
                .name("키보드")
                .description("기계식 청축")
                .productItemList(List.of(
                        ProductItemDto.builder()
                                .id(100L).name("청축").price(120000).count(1).build()
                ))
                .build();

        given(cartService.addCart(anyLong(), any())).willReturn(sampleCart());

        mockMvc.perform(post("/customer/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(form)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(1L))
                .andDo(document("customer-cart-add"));
    }

    @DisplayName("GET /customer/cart - 현재 로그인한 고객의 장바구니 조회")
    @Test
    void getCart() throws Exception {
        given(cartService.getCart(anyLong())).willReturn(sampleCart());

        mockMvc.perform(get("/customer/cart")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(1L))
                .andDo(document("customer-cart-get"));
    }

    @DisplayName("PUT /customer/cart - 장바구니 수정")
    @Test
    void updateCart() throws Exception {
        Cart cart = sampleCart();

        given(cartService.updateCart(anyLong(), any())).willReturn(cart);

        mockMvc.perform(put("/customer/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cart)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(1L))
                .andDo(document("customer-cart-update"));
    }

    @DisplayName("POST /customer/cart/order - 장바구니 주문 (멱등성 키 헤더)")
    @Test
    void orderCart() throws Exception {
        Cart cart = sampleCart();

        Order order = Order.builder()
                .id(500L)
                .status(OrderStatus.PENDING)
                .totalPrice(0)
                .items(new ArrayList<>())
                .build();
        OrderDto orderDto = OrderDto.from(order);

        given(orderService.order(anyLong(), anyString(), any())).willReturn(orderDto);
        given(idempotencyService.execute(any(), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

        mockMvc.perform(post("/customer/cart/order")
                        .header("Idempotency-Key", "test-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cart)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(500L))
                .andDo(document("customer-cart-order"));
    }
}
