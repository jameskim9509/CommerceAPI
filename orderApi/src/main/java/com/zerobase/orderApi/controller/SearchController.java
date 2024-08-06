package com.zerobase.orderApi.controller;

import com.zerobase.orderApi.dto.ProductDto;
import com.zerobase.orderApi.dto.ProductItemDto;
import com.zerobase.orderApi.dto.ProductItemSearchCondDto;
import com.zerobase.orderApi.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/search")
public class SearchController {

    private final SearchService searchService;

    @PreAuthorize("hasAnyRole('SELLER','CUSTOMER')")
    @GetMapping("/product")
    public ResponseEntity<List<ProductDto>> searchByProductName(
            @RequestParam("name") String name
    ){
        return ResponseEntity.ok(
                searchService.searchByProductName(name)
        );
    }

    @PreAuthorize("hasAnyRole('SELLER','CUSTOMER')")
    @GetMapping("/product/detail")
    public ResponseEntity<ProductDto> getProductDetailById(
            @RequestParam("productId") Long productId
    ){
        return ResponseEntity.ok(
                searchService.getByProductId(productId)
        );
    }

    @PreAuthorize("hasAnyRole('SELLER','CUSTOMER')")
    @GetMapping("/product/list")
    public ResponseEntity<List<ProductDto>> getProductById(
            @RequestParam("productId") List<Long> productIds
    ){
        return ResponseEntity.ok(
                searchService.getListByProductId(productIds)
        );
    }

    @PreAuthorize("hasAnyRole('SELLER','CUSTOMER')")
    @GetMapping("/item")
    public ResponseEntity<List<ProductItemDto>> searchItemByCond(
            @PageableDefault final Pageable pageable,
            @RequestBody ProductItemSearchCondDto form
    ){
        return ResponseEntity.ok(
                searchService.searchProductItems(form, pageable)
        );
    }
}
