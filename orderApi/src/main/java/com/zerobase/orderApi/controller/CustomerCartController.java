package com.zerobase.orderApi.controller;

import com.zerobase.orderApi.domain.Cart;
import com.zerobase.orderApi.dto.AddProductCartForm;
import com.zerobase.orderApi.security.CustomUserDetails;
import com.zerobase.orderApi.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/customer/cart")
public class CustomerCartController {
    private final CartService cartService;

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping
    public ResponseEntity<Cart> addCart(
            @RequestBody AddProductCartForm form
    ){
        CustomUserDetails userDetails =
                (CustomUserDetails) SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal();

        return ResponseEntity.ok(cartService.addCart(userDetails.getId(), form));
    }
}
