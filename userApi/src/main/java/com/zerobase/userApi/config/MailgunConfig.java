package com.zerobase.userApi.config;

import feign.RequestInterceptor;
import feign.auth.BasicAuthRequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MailgunConfig {

    @Value("${mailgun.apiKey}")
    String apiKey;

    @Bean
    public RequestInterceptor basicAuthRequestInterceptor()
    {
        return new BasicAuthRequestInterceptor("api", apiKey);
    }
}
