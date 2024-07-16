package com.zerobase.gateway.filter;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

@Component
public class FilterUtils {

    public static String transactionIdHeader = "transaction-id";

    public String getTransactionId(HttpHeaders headers)
    {
        if(headers.get(transactionIdHeader) != null)
        {
            return headers.get(transactionIdHeader).stream()
                    .findFirst()
                    .get();
        }
        else return null;
    }

    public ServerWebExchange setTransactionId(ServerWebExchange exchange, String transactionId)
    {
        return exchange.mutate().request(
                exchange.getRequest().mutate()
                        .header(transactionIdHeader, transactionId)
                        .build()
        ).build();
    }
}
