package com.zerobase.orderApi.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@EnableFeignClients(basePackages = "com.zerobase.orderApi")
@Configuration
public class FeignConfig {
}
