package com.zerobase.userApi.controller;

import com.zerobase.userApi.dto.SignupDto;
import com.zerobase.userApi.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customer/signup")
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerService customerService;

    @PostMapping
    public ResponseEntity<SignupDto.Output> customerSignUp(
            @RequestBody SignupDto.Input form
    )
    {
        return ResponseEntity.ok(customerService.signUp(form));
    }

    @PutMapping("/verify")
    public ResponseEntity<SignupDto.Output> customerVerify(
            @RequestParam("email") String email, @RequestParam("code") String code
    )
    {
        return ResponseEntity.ok(customerService.verfiyEmail(email, code));
    }

}
