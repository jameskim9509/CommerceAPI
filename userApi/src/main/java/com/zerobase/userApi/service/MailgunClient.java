package com.zerobase.userApi.service;

import com.zerobase.userApi.config.MailgunConfig;
import com.zerobase.userApi.dto.SendMailDto;
import feign.Response;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(
        name = "mailgun",
        url = "https://api.mailgun.net/v3",
        configuration = MailgunConfig.class
)
@Qualifier("mailgun")
public interface MailgunClient {

    @PostMapping("/sandboxbaefebde97ff412b880c7cc30355f688.mailgun.org/messages")
    ResponseEntity sendEmail(@SpringQueryMap SendMailDto form);
}
