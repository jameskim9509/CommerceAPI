package com.zerobase.userApi.controller.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobase.userApi.domain.customer.Customer;
import com.zerobase.userApi.dto.ChangeBalanceDto;
import com.zerobase.userApi.dto.SigninDto;
import com.zerobase.userApi.dto.SignupDto;
import com.zerobase.userApi.repository.customer.CustomerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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

    @DisplayName("회원가입 및 검증, 로그인 후 예치금 입금 성공")
    @Test
    void customerControllerTest() throws Exception {
        //given
        SignupDto.Input signupForm = SignupDto.Input.builder()
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
                            .content(objectMapper.writeValueAsString(signupForm)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.message")
                                .value("Verification Email이 발송되었습니다."));

        Customer customer = customerRepository.findByEmail(signupForm.getEmail()).get();

        url = "/customer/signup/verify?email={1}&code={2}";

        //when, then
        mockMvc.perform(put(url, customer.getEmail(), customer.getVerificationCode())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Verification에 성공하였습니다."));

        //given
        SigninDto.Input loginForm = SigninDto.Input.builder()
                .email("bmom22@naver.com")
                .password("1234")
                .build();

        url = "/customer/login";

        //when, then
        MvcResult result = mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginForm)))
                        .andExpect(status().isOk())
                        .andReturn();

        //given
        String jwt = result.getResponse().getContentAsString();
        url = "/customer/test";

        //when, then
        mockMvc.perform(get(url)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(content().string("name님, test에 성공하였습니다."));

        //given
        ChangeBalanceDto.Input balanceForm =
                ChangeBalanceDto.Input.builder()
                        .from("james")
                        .money(10000)
                        .message("save 10000 won")
                        .build();

        url = "/customer/balance";

        //when, then
        mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + jwt)
                        .content(objectMapper.writeValueAsString(balanceForm)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance")
                        .value("10000"));
    }
}