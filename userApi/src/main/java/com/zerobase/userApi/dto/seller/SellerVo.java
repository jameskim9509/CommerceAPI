package com.zerobase.userApi.dto.seller;

import com.zerobase.userApi.domain.seller.Seller;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerVo {
    private Long id;

    private String email;
    private String name;
    private String password;
    private LocalDate birth;
    private String phoneNum;

    private LocalDateTime verifyExpiredAt;
    private String verificationCode;
    private boolean verify;

    private List<String> roles;

    public static SellerVo from(Seller entity)
    {
        return SellerVo.builder()
                .id(entity.getId())
                .email(entity.getEmail())
                .name(entity.getName())
                .password(entity.getPassword())
                .birth(entity.getBirth())
                .phoneNum(entity.getPhoneNum())
                .verifyExpiredAt(entity.getVerifyExpiredAt())
                .verificationCode(entity.getVerificationCode())
                .verify(entity.isVerify())
                .roles(entity.getRoles())
                .build();
    }
}
