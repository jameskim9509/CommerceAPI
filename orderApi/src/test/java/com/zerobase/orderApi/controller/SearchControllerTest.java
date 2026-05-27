package com.zerobase.orderApi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobase.orderApi.dto.ProductDto;
import com.zerobase.orderApi.dto.ProductItemDto;
import com.zerobase.orderApi.dto.ProductItemSearchCondDto;
import com.zerobase.orderApi.security.CustomUserDetails;
import com.zerobase.orderApi.service.SearchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith({MockitoExtension.class, RestDocumentationExtension.class})
class SearchControllerTest {

    @Mock
    private SearchService searchService;

    @InjectMocks
    private SearchController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
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

    private ProductDto sampleProduct(Long id, String name) {
        return ProductDto.builder()
                .id(id)
                .name(name)
                .description("샘플 상품")
                .productItemList(List.of())
                .build();
    }

    @DisplayName("GET /search/product?name={name} - 상품명으로 검색")
    @Test
    void searchByProductName() throws Exception {
        given(searchService.searchByProductName(anyString()))
                .willReturn(List.of(sampleProduct(10L, "키보드")));

        mockMvc.perform(get("/search/product")
                        .param("name", "키보드")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10L))
                .andDo(document("search-product-by-name"));
    }

    @DisplayName("GET /search/product/detail?productId={id} - 상품 상세 조회")
    @Test
    void getProductDetailById() throws Exception {
        given(searchService.getByProductId(anyLong()))
                .willReturn(sampleProduct(10L, "키보드"));

        mockMvc.perform(get("/search/product/detail")
                        .param("productId", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andDo(document("search-product-detail"));
    }

    @DisplayName("GET /search/product/list?productId={ids} - 상품 다건 조회")
    @Test
    void getProductByIds() throws Exception {
        given(searchService.getListByProductId(any()))
                .willReturn(List.of(
                        sampleProduct(10L, "키보드"),
                        sampleProduct(11L, "마우스")
                ));

        mockMvc.perform(get("/search/product/list")
                        .param("productId", "10", "11")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andDo(document("search-product-list"));
    }

    @DisplayName("GET /search/item - 상품 아이템 조건 검색")
    @Test
    void searchItemByCond() throws Exception {
        ProductItemSearchCondDto cond = ProductItemSearchCondDto.builder()
                .itemName("청축")
                .priceMin(100000)
                .priceMax(200000)
                .build();

        ProductItemDto item = ProductItemDto.builder()
                .id(100L).name("청축").price(120000).count(5).build();

        given(searchService.searchProductItems(any(), any(Pageable.class)))
                .willReturn(List.of(item));

        mockMvc.perform(get("/search/item")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100L))
                .andDo(document("search-item-by-cond"));
    }
}
