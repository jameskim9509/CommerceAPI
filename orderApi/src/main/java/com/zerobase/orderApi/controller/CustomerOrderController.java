package com.zerobase.orderApi.controller;

import com.zerobase.orderApi.dto.OrderDto;
import com.zerobase.orderApi.security.CustomUserDetails;
import com.zerobase.orderApi.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/customer/orders")
public class CustomerOrderController {

    private final OrderService orderService;

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDto> getOrder(@PathVariable Long orderId) {
        CustomUserDetails userDetails =
                (CustomUserDetails) SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal();
        return ResponseEntity.ok(orderService.getOrder(userDetails.getId(), orderId));
    }
}
