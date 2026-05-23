package com.zerobase.userApi.controller.seller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobase.userApi.domain.seller.Seller;
import com.zerobase.userApi.dto.SigninDto;
import com.zerobase.userApi.dto.SignupDto;
import com.zerobase.userApi.repository.seller.SellerRepository;
import com.zerobase.userApi.service.MailgunClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SellerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SellerRepository sellerRepository;

    @MockBean
    private MailgunClient mailgunClient;

    @BeforeEach
    void stubMailgun() {
        given(mailgunClient.sendEmail(any())).willReturn(ResponseEntity.ok().build());
    }

    @DisplayName("회원가입 및 검증, 로그인 성공")
    @Test
    void sellerSignUpAndVerifyAndLogin() throws Exception {
        //given
        SignupDto.Input signupForm = SignupDto.Input.builder()
                .name("name")
                .birth(LocalDate.now())
                .email("bmom22@naver.com")
                .password("1234")
                .phoneNum("01012345678")
                .build();

        String url;
        url = "/seller/signup";

        //when, then
        mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupForm)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Verification Email이 발송되었습니다."));

        Seller seller = sellerRepository.findByEmail(signupForm.getEmail()).get();

        url = "/seller/signup/verify?email={1}&code={2}";

        //when, then
        mockMvc.perform(put(url, seller.getEmail(), seller.getVerificationCode())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Verification에 성공하였습니다."));

        //given
        SigninDto.Input loginForm = SigninDto.Input.builder()
                .email("bmom22@naver.com")
                .password("1234")
                .build();

        url = "/seller/login";

        //when, then
        MvcResult result = mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginForm)))
                .andExpect(status().isOk())
                .andReturn();

        //given
        String jwt = result.getResponse().getContentAsString();
        url = "/seller/test";

        mockMvc.perform(get(url)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(content().string("name님, test에 성공하였습니다."));
    }
}