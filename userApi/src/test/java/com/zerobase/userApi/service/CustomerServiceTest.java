package com.zerobase.userApi.service;

import com.zerobase.userApi.dto.CustomerDto;
import com.zerobase.userApi.dto.SendMailDto;
import com.zerobase.userApi.dto.SignupDto;
import feign.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CustomerServiceTest {

    @Autowired
    private CustomerService service;

    @DisplayName("signUp 성공")
    @Test
    void signUp()
    {
        SignupDto.Input form =
                SignupDto.Input.builder()
                        .name("name")
                        .birth(LocalDate.now())
                        .email("abc@naver.com")
                        .password("1234")
                        .phoneNum("01012345678")
                        .build();

        SignupDto.Output output = service.signUp(form);

        assertTrue(service.isCustomerExistByEmail(form.getName()));
        assertEquals("Verification Email이 발송되었습니다.", output.getMessage());
    }

    @DisplayName("sendMail 성공")
    @Test
    void sendMail()
    {
        SendMailDto form =
                SendMailDto.builder()
                        .from("user1 <user1@sandboxbaefebde97ff412b880c7cc30355f688.mailgun.org>")
                        .to("bmom22@naver.com")
                        .subject("TestMail")
                        .text("blablabla...")
                        .build();

        ResponseEntity response = service.sendEmail(form);

        assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode());
    }
}