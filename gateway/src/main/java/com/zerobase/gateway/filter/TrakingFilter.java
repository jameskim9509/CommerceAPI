package com.zerobase.gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Order(1)
@Component
public class TrakingFilter implements GlobalFilter {

    private final FilterUtils filterUtils;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        HttpHeaders requestHeaders = exchange.getRequest().getHeaders();
        if(isTransactionIdPresent(requestHeaders))
        {
            log.info("transaction-Id : {}, uri : {}", filterUtils.getTransactionId(requestHeaders), exchange.getRequest().getURI());
        }
        else
        {
            String transactionId = generateTransactionId();
            exchange = filterUtils.setTransactionId(exchange, transactionId);
            log.info("transaction-Id : {}, uri : {}", transactionId, exchange.getRequest().getURI());
        }
        return chain.filter(exchange);
    }

    private boolean isTransactionIdPresent(HttpHeaders headers)
    {
        if(filterUtils.getTransactionId(headers) != null) return true;
        else return false;
    }

    private String generateTransactionId()
    {
        return UUID.randomUUID().toString();
    }
}
