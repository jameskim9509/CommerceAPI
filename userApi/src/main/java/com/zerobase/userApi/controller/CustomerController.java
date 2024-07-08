package com.zerobase.userApi.controller;

import com.zerobase.userApi.domain.Customer;
import com.zerobase.userApi.dto.SigninDto;
import com.zerobase.userApi.dto.SignupDto;
import com.zerobase.userApi.security.JwtTokenProvider;
import com.zerobase.userApi.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customer")
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerService customerService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/signup")
    public ResponseEntity<SignupDto.Output> customerSignUp(
            @RequestBody SignupDto.Input form
    )
    {
        return ResponseEntity.ok(customerService.signUp(form));
    }

    @PutMapping("/signup/verify")
    public ResponseEntity<SignupDto.Output> customerVerify(
            @RequestParam("email") String email, @RequestParam("code") String code
    )
    {
        return ResponseEntity.ok(customerService.verfiyEmail(email, code));
    }

    @PostMapping("/login")
    public ResponseEntity<String> customerSignIn(
            @RequestBody SigninDto.Input form
    )
    {
        Customer customer = customerService.findValidCustomer(form.getEmail(), form.getPassword());
        return ResponseEntity.ok(jwtTokenProvider.generateToken(customer.getEmail()));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/test")
    public ResponseEntity<String> testSuccess()
    {
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok( name + "님, test에 성공하였습니다.");
    }
}
