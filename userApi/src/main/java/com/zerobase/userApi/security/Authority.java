package com.zerobase.userApi.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Authority {
    CUSTOMER("ROLE_CUSTOMER");

    private final String role;
}
