package com.zerobase.userApi.controller.seller;

import com.zerobase.userApi.dto.SigninDto;
import com.zerobase.userApi.dto.SignupDto;
import com.zerobase.userApi.dto.seller.SellerVo;
import com.zerobase.userApi.security.JwtTokenProvider;
import com.zerobase.userApi.service.seller.SellerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/seller")
@RequiredArgsConstructor
public class SellerController {
    private final SellerService sellerService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/signup")
    public ResponseEntity<SignupDto.Output> sellerSignUp(
            @RequestBody SignupDto.Input form
    )
    {
        return ResponseEntity.ok(sellerService.signUp(form));
    }

    @PutMapping("/signup/verify")
    public ResponseEntity<SignupDto.Output> sellerVerify(
            @RequestParam("email") String email, @RequestParam("code") String code
    )
    {
        return ResponseEntity.ok(sellerService.verfiyEmail(email, code));
    }

    @PostMapping("/login")
    public ResponseEntity<String> sellerSignIn(
            @RequestBody SigninDto.Input form
    )
    {
        SellerVo sellerVo =
                sellerService.findValidSeller(form.getEmail(), form.getPassword());

        return ResponseEntity.ok(
                jwtTokenProvider.generateToken(
                        sellerVo.getEmail(), sellerVo.getRoles(), sellerVo.getId()
                )
        );
    }

    @PreAuthorize("hasRole('SELLER')")
    @GetMapping("/test")
    public ResponseEntity<String> testSuccess()
    {
        String name =
                SecurityContextHolder.getContext().getAuthentication().getName();

        return ResponseEntity.ok( name + "님, test에 성공하였습니다.");
    }
}
