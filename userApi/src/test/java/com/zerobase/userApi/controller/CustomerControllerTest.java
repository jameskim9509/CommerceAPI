package com.zerobase.userApi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobase.userApi.domain.customer.Customer;
import com.zerobase.userApi.dto.SignupDto;
import com.zerobase.userApi.repository.customer.CustomerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @DisplayName("회원가입 및 검증 성공")
    @Test
    void customerSignUpAndVerify() throws Exception {
        //given
        SignupDto.Input form = SignupDto.Input.builder()
                .name("name")
                .birth(LocalDate.now())
                .email("bmom22@naver.com")
                .password("1234")
                .phoneNum("01012345678")
                .build();

        String url;
        url = "/customer/signup";

        //when, then
        mockMvc.perform(post(url)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(form)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.message")
                                .value("Verification Email이 발송되었습니다."));

        Customer customer = customerRepository.findByEmail(form.getEmail()).get();

        url = "/customer/signup/verify?email={1}&code={2}";

        //when, then
        mockMvc.perform(put(url, customer.getEmail(), customer.getVerificationCode())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Verification에 성공하였습니다."));
    }
}