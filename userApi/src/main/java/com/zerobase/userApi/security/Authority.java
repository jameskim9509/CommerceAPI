package com.zerobase.userApi.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Authority {
    CUSTOMER("ROLE_CUSTOMER"),
    SELLER("ROLE_SELLER");

    private final String role;
}
