package com.zerobase.userApi.dto;

import com.zerobase.userApi.domain.Customer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

public class SignupDto {

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Builder
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
                    .build();
        }
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Builder
    public static class Output {
        private String name;
    }
}
