package com.zerobase.orderApi.controller;

import com.zerobase.orderApi.domain.OrderStatus;
import com.zerobase.orderApi.dto.OrderDto;
import com.zerobase.orderApi.security.CustomUserDetails;
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
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith({MockitoExtension.class, RestDocumentationExtension.class})
class CustomerOrderControllerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private CustomerOrderController controller;

    private MockMvc mockMvc;

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

    @DisplayName("GET /customer/orders/{orderId} - 주문 상세 조회")
    @Test
    void getOrder() throws Exception {
        OrderDto orderDto = OrderDto.builder()
                .orderId(500L)
                .status(OrderStatus.PAID)
                .totalPrice(120000)
                .items(new ArrayList<>())
                .build();

        given(orderService.getOrder(anyLong(), anyLong())).willReturn(orderDto);

        mockMvc.perform(get("/customer/orders/{orderId}", 500L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(500L))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andDo(document("customer-order-get"));
    }
}
