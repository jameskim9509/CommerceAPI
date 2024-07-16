package com.zerobase.orderApi.service;

import com.zerobase.orderApi.dto.ChangeBalanceDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "user-api",
        url = "http://localhost:8081/customer"
)
public interface UserClient {

    @PostMapping("/balance")
    ResponseEntity<ChangeBalanceDto.Output> changeBalance(
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody ChangeBalanceDto.Input form
    );
}
