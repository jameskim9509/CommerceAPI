package com.zerobase.userApi.controller;

import com.zerobase.userApi.domain.Customer;
import com.zerobase.userApi.dto.SignupDto;
import com.zerobase.userApi.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class CustomerControllerTest {

    @Autowired
    private CustomerRepository customerRepository;

    private RestTemplate restTemplate;

    @BeforeEach
    void init()
    {
        restTemplate = new RestTemplate();
    }

    @DisplayName("회원가입 및 검증 성공")
    @Test
    void customerSignUpAndVerify() {
        //given
        SignupDto.Input form = SignupDto.Input.builder()
                .name("name")
                .birth(LocalDate.now())
                .email("abc@naver.com")
                .password("1234")
                .phoneNum("01012345678")
                .build();

        String url = "http://localhost:8081/customer/signup";
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<SignupDto.Input> signUpRequest = new HttpEntity<>(form, httpHeaders);

        ResponseEntity<SignupDto.Output> response;

        //when
        response =
                restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        signUpRequest,
                        SignupDto.Output.class
                );

        //then
        assertEquals(HttpStatus.valueOf(200), response.getStatusCode());

        //given
        Customer customer = customerRepository.findByEmail(form.getEmail()).get();

        UriComponentsBuilder uriBuilder =
                UriComponentsBuilder.fromHttpUrl("<http://localhost:8081/customer/signup/verfiy>")
                        .queryParam("email", customer.getEmail())
                        .queryParam("code", customer.getVerificationCode());
        HttpEntity header = new HttpEntity<>(httpHeaders);

        //when
        response = restTemplate.exchange(
                        uriBuilder.toUriString(),
                        HttpMethod.PUT,
                        header,
                        SignupDto.Output.class
                );

        //then
        assertEquals(HttpStatus.valueOf(200), response.getStatusCode());
    }
}