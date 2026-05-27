package com.zerobase.orderApi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobase.orderApi.dto.AddProductForm;
import com.zerobase.orderApi.dto.AddProductItemForm;
import com.zerobase.orderApi.dto.UpdateProductForm;
import com.zerobase.orderApi.dto.UpdateProductItemForm;
import com.zerobase.orderApi.security.CustomUserDetails;
import com.zerobase.orderApi.service.ProductService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith({MockitoExtension.class, RestDocumentationExtension.class})
class SellerProductControllerTest {

    @Mock
    private ProductService productService;

    @InjectMocks
    private SellerProductController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .apply(documentationConfiguration(restDocumentation))
                .build();

        CustomUserDetails principal =
                new CustomUserDetails("seller@test.com", 99L, List.of("ROLE_SELLER"));

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

    @DisplayName("POST /seller/product - 상품 등록")
    @Test
    void addProduct() throws Exception {
        AddProductForm.Input form = AddProductForm.Input.builder()
                .name("키보드")
                .description("기계식 청축")
                .addProductItemForms(new ArrayList<>())
                .build();

        AddProductForm.Output output = AddProductForm.Output.builder()
                .productId(10L)
                .sellerId(99L)
                .name("키보드")
                .description("기계식 청축")
                .addProductItemForms(new ArrayList<>())
                .build();

        given(productService.addProduct(anyLong(), any())).willReturn(output);

        mockMvc.perform(post("/seller/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(form)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(10L))
                .andDo(document("seller-product-add"));
    }

    @DisplayName("POST /seller/product/item - 상품 아이템 등록")
    @Test
    void addProductItem() throws Exception {
        AddProductItemForm.Input form = AddProductItemForm.Input.builder()
                .productId(10L)
                .name("청축")
                .price(120000)
                .count(5)
                .build();

        AddProductForm.Output output = AddProductForm.Output.builder()
                .productId(10L)
                .sellerId(99L)
                .name("키보드")
                .description("기계식 청축")
                .addProductItemForms(new ArrayList<>())
                .build();

        given(productService.addProductItem(anyLong(), any())).willReturn(output);

        mockMvc.perform(post("/seller/product/item")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(form)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(10L))
                .andDo(document("seller-product-item-add"));
    }

    @DisplayName("PUT /seller/product - 상품 수정")
    @Test
    void updateProduct() throws Exception {
        UpdateProductForm.Input form = UpdateProductForm.Input.builder()
                .productId(10L)
                .name("키보드 v2")
                .description("기계식 갈축")
                .updateProductItemForms(new ArrayList<>())
                .build();

        UpdateProductForm.Output output = UpdateProductForm.Output.builder()
                .id(10L)
                .sellerId(99L)
                .name("키보드 v2")
                .description("기계식 갈축")
                .updatedProductItemList(new ArrayList<>())
                .build();

        given(productService.updateProduct(anyLong(), any())).willReturn(output);

        mockMvc.perform(put("/seller/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(form)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andDo(document("seller-product-update"));
    }

    @DisplayName("PUT /seller/product/item - 상품 아이템 수정")
    @Test
    void updateProductItem() throws Exception {
        UpdateProductItemForm.Input form = UpdateProductItemForm.Input.builder()
                .id(100L)
                .productId(10L)
                .name("갈축")
                .price(130000)
                .count(3)
                .build();

        UpdateProductItemForm.Output output = UpdateProductItemForm.Output.builder()
                .id(100L)
                .sellerId(99L)
                .name("갈축")
                .price(130000)
                .count(3)
                .build();

        given(productService.updateProductItem(anyLong(), any())).willReturn(output);

        mockMvc.perform(put("/seller/product/item")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(form)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100L))
                .andDo(document("seller-product-item-update"));
    }

    @DisplayName("DELETE /seller/product?id={id} - 상품 삭제")
    @Test
    void deleteProduct() throws Exception {
        given(productService.deleteProduct(anyLong(), anyLong())).willReturn("DELETED");

        mockMvc.perform(delete("/seller/product")
                        .param("id", "10"))
                .andExpect(status().isOk())
                .andExpect(content().string("DELETED"))
                .andDo(document("seller-product-delete"));
    }

    @DisplayName("DELETE /seller/product/item?id={id} - 상품 아이템 삭제")
    @Test
    void deleteProductItem() throws Exception {
        given(productService.deleteProductItem(anyLong(), anyLong())).willReturn("DELETED");

        mockMvc.perform(delete("/seller/product/item")
                        .param("id", "100"))
                .andExpect(status().isOk())
                .andExpect(content().string("DELETED"))
                .andDo(document("seller-product-item-delete"));
    }
}
