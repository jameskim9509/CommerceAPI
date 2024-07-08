package com.zerobase.userApi.dto.customer;

import com.zerobase.userApi.domain.customer.Customer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerDto {
    private String email;
    private String name;
    private String password;
    private LocalDate birth;
    private String phoneNum;

    private LocalDateTime verifyExpiredAt;
    private String verificationCode;
    private boolean verify;

    public static CustomerDto from(Customer entity)
    {
        return CustomerDto.builder()
                .email(entity.getEmail())
                .name(entity.getName())
                .password(entity.getPassword())
                .birth(entity.getBirth())
                .phoneNum(entity.getPhoneNum())
                .verifyExpiredAt(entity.getVerifyExpiredAt())
                .verificationCode(entity.getVerificationCode())
                .verify(entity.isVerify())
                .build();
    }
}
