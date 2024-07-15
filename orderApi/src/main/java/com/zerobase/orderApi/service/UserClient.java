package com.zerobase.orderApi.service;

import com.zerobase.orderApi.dto.ChangeBalanceDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "user-api",
        url = "https://api.mailgun.net/v3"
)
public interface UserClient {

    @PostMapping("/customer/balance")
    ResponseEntity<ChangeBalanceDto.Output> changeBalance(
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody ChangeBalanceDto.Input form
    );
}
