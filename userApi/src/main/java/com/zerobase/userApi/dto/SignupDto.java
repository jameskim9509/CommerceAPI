package com.zerobase.userApi.dto;

import com.zerobase.userApi.domain.Customer;
import com.zerobase.userApi.security.Authority;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

public class SignupDto {

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class Input {
        private String email;
        private String name;
        private String password;
        private LocalDate birth;
        private String phoneNum;

        public Customer toCustomerEntity()
        {
            return Customer.builder()
                    .email(this.email)
                    .name(this.name)
                    .password(this.password)
                    .birth(this.birth)
                    .phoneNum(this.phoneNum)
                    .roles(List.of(Authority.CUSTOMER.getRole()))
                    .build();
        }
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Builder
    public static class Output {
        private String message;
    }
}
