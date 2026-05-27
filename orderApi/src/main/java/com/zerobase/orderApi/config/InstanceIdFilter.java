package com.zerobase.orderApi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * ADR-005 시나리오 3 측정 지원:
 *  - 모든 응답에 X-Instance-Id 헤더를 부여해 어느 orderApi 인스턴스가 처리했는지 식별.
 *  - k6 부하 테스트가 이 헤더를 집계해 LB 분배 균등성을 검증.
 *
 * 인스턴스 식별 우선순위:
 *  1. INSTANCE_ID env (운영자가 명시적으로 지정)
 *  2. HOSTNAME env (docker 컨테이너 ID, 기본)
 *  3. InetAddress.getLocalHost().getHostName() (fallback)
 */
@Component
public class InstanceIdFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Instance-Id";

    private final String instanceId;

    public InstanceIdFilter(
            @Value("${INSTANCE_ID:}") String configuredId,
            @Value("${HOSTNAME:}") String hostname
    ) {
        if (!configuredId.isBlank()) {
            this.instanceId = configuredId;
        } else if (!hostname.isBlank()) {
            this.instanceId = hostname;
        } else {
            this.instanceId = resolveHostnameFallback();
        }
    }

    private static String resolveHostnameFallback() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        res.setHeader(HEADER, instanceId);
        chain.doFilter(req, res);
    }
}
