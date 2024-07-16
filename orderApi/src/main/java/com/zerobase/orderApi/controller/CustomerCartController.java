package com.zerobase.orderApi.controller;

import com.zerobase.orderApi.domain.Cart;
import com.zerobase.orderApi.dto.AddProductCartForm;
import com.zerobase.orderApi.security.CustomUserDetails;
import com.zerobase.orderApi.service.CartService;
import com.zerobase.orderApi.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/customer/cart")
public class CustomerCartController {
    private final CartService cartService;
    private final OrderService orderService;

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

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping
    public ResponseEntity<Cart> getCart()
    {
        CustomUserDetails userDetails =
                (CustomUserDetails) SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal();

        return ResponseEntity.ok(cartService.getCart(userDetails.getId()));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PutMapping
    public ResponseEntity<Cart> updateCart(
            @RequestBody Cart cart
    )
    {
        CustomUserDetails userDetails =
                (CustomUserDetails) SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal();

        return ResponseEntity.ok(cartService.updateCart(userDetails.getId(), cart));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/order")
    public ResponseEntity<Cart> orderCart(
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody Cart cart
    )
    {
        CustomUserDetails userDetails =
                (CustomUserDetails) SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal();

        return ResponseEntity.ok(
                orderService.order(
                        bearerToken,
                        userDetails.getId(),
                        userDetails.getUsername(),
                        cart
                )
        );
    }
}
