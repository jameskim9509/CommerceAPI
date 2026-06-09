package com.zerobase.userApi.service.customer;

import com.zerobase.userApi.dto.SendMailDto;
import com.zerobase.userApi.dto.SignupDto;
import com.zerobase.userApi.service.GmailClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willDoNothing;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class CustomerServiceTest {

    @Autowired
    private CustomerService service;

    @MockBean
    private GmailClient gmailClient;

    @BeforeEach
    void stubGmail() {
        willDoNothing().given(gmailClient).sendEmail(any());
    }

    @DisplayName("signUp 성공")
    @Test
    void signUp()
    {
        SignupDto.Input form =
                SignupDto.Input.builder()
                        .name("name")
                        .birth(LocalDate.now())
                        .email("bmom22@naver.com")
                        .password("1234")
                        .phoneNum("01012345678")
                        .build();

        SignupDto.Output output = service.signUp(form);

        assertTrue(service.isCustomerExistByEmail(form.getEmail()));
        assertEquals("Verification Email이 발송되었습니다.", output.getMessage());
    }

    @DisplayName("sendMail 성공")
    @Test
    void sendMail()
    {
        SendMailDto form =
                SendMailDto.builder()
                        .from("<CommerceAPI@application.com>")
                        .to("bmom22@naver.com")
                        .subject("TestMail")
                        .text("blablabla...")
                        .build();

        assertDoesNotThrow(() -> service.sendEmail(form));
    }
}