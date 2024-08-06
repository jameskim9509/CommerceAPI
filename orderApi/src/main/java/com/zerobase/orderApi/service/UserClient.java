package com.zerobase.orderApi.service;

import com.zerobase.orderApi.dto.ChangeBalanceDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "user-api",
        url = "http://ec2-43-202-0-143.ap-northeast-2.compute.amazonaws.com/order/customer"
)
public interface UserClient {

    @PostMapping("/balance")
    ResponseEntity<ChangeBalanceDto.Output> changeBalance(
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody ChangeBalanceDto.Input form
    );
}
