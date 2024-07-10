package com.zerobase.orderApi.controller;

import com.zerobase.orderApi.dto.AddProductForm;
import com.zerobase.orderApi.dto.AddProductItemForm;
import com.zerobase.orderApi.dto.ProductDto;
import com.zerobase.orderApi.security.CustomUserDetails;
import com.zerobase.orderApi.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/seller/product")
public class SellerProductController {
    private final ProductService productService;

    @PreAuthorize("hasRole('SELLER')")
    @PostMapping
    public ResponseEntity<AddProductForm.Output> addProduct(
            @RequestBody AddProductForm.Input form
    )
    {
        CustomUserDetails userDetails =
                (CustomUserDetails)SecurityContextHolder
                                        .getContext()
                                        .getAuthentication()
                                        .getPrincipal();

        return ResponseEntity.ok(
                productService.addProduct(userDetails.getId(), form)
        );
    }

    @RequestMapping("/item")
    @PreAuthorize("hasRole('SELLER')")
    @PostMapping
    public ResponseEntity<AddProductForm.Output> addProductItem(
            @RequestBody AddProductItemForm.Input form
    )
    {
        CustomUserDetails userDetails =
                (CustomUserDetails)SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal();

        return ResponseEntity.ok(
                productService.addProductItem(userDetails.getId(), form)
        );
    }
}
