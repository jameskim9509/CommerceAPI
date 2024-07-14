package com.zerobase.orderApi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobase.orderApi.domain.Cart;
import com.zerobase.orderApi.exception.CustomException;
import com.zerobase.orderApi.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisClientService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public <T> T get(Long key, Class<T> classType)
    {
        return get(key.toString(), classType);
    }

    public <T> T get(String key, Class<T> classType)
    {
        String redisValue = (String) redisTemplate.opsForValue().get(key);
        if(ObjectUtils.isEmpty(redisValue))
        {
            return null;
        }
        else {
            try {
                return objectMapper.readValue(redisValue, classType);
            } catch (JsonProcessingException e) {
                log.error("Parsing error", e);
                return null;
            }
        }
    }

    public void put(Long key, Cart cart){
        put(key.toString(), cart);
    }

    public void put(String key, Cart cart)
    {
        try{
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(cart));
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.CART_CHANGE_FAILED);
        }
    }
}
