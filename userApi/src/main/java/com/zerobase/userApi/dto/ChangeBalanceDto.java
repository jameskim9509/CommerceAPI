package com.zerobase.userApi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class ChangeBalanceDto {
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Builder
    public static class Input
    {
        Integer money;
        String message;
        String from;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Builder
    public static class Output
    {
        Integer balance;
    }
}
