package com.zerobase.orderApi.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerobase.orderApi.exception.CustomException;
import com.zerobase.orderApi.exception.ErrorCode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.ResponseEntity;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyServiceIntegrationTest {

    private static RedisServer redisServer;
    private static LettuceConnectionFactory connectionFactory;
    private static RedisTemplate<String, Object> redisTemplate;
    private static IdempotencyService idempotencyService;

    @BeforeAll
    static void startRedis() throws IOException {
        int port = findFreePort();
        redisServer = new RedisServer(port);
        redisServer.start();

        connectionFactory = new LettuceConnectionFactory("localhost", port);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        StringRedisSerializer s = new StringRedisSerializer();
        redisTemplate.setKeySerializer(s);
        redisTemplate.setValueSerializer(s);
        redisTemplate.afterPropertiesSet();

        idempotencyService = new IdempotencyService(redisTemplate, new ObjectMapper());
    }

    @AfterAll
    static void stopRedis() throws IOException {
        if (connectionFactory != null) connectionFactory.destroy();
        if (redisServer != null) redisServer.stop();
    }

    @BeforeEach
    void flush() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    @Test
    @DisplayName("같은 키로 두 번 호출 — action 은 한 번만 실행되고 두 응답이 동일")
    void sameKey_actionRunsOnceAndResponsesAreEqual() {
        AtomicInteger callCount = new AtomicInteger();

        ResponseEntity<?> first = idempotencyService.execute("ORDER-K1",
                () -> {
                    callCount.incrementAndGet();
                    return ResponseEntity.ok(Map.of("orderId", 100));
                });
        ResponseEntity<?> second = idempotencyService.execute("ORDER-K1",
                () -> {
                    callCount.incrementAndGet();
                    return ResponseEntity.ok(Map.of("orderId", 999));
                });

        assertThat(callCount.get()).isEqualTo(1);
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> secondBody = (Map<String, Object>) second.getBody();
        assertThat(secondBody).containsEntry("orderId", 100);
    }

    @Test
    @DisplayName("병렬 호출 — 첫 호출만 action 실행, 나머지는 409 또는 캐시 응답")
    void concurrentRequests_actionRunsOnce() throws Exception {
        int threads = 10;
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger actionExecutions = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            exec.submit(() -> {
                try {
                    start.await();
                    idempotencyService.execute("ORDER-CONCURRENT", () -> {
                        actionExecutions.incrementAndGet();
                        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                        return ResponseEntity.ok("done");
                    });
                } catch (CustomException e) {
                    if (e.getErrorCode() == ErrorCode.DUPLICATE_REQUEST_IN_PROGRESS) {
                        conflicts.incrementAndGet();
                    }
                }
                return null;
            });
        }
        start.countDown();
        exec.shutdown();
        boolean done = exec.awaitTermination(10, TimeUnit.SECONDS);
        assertThat(done).isTrue();

        assertThat(actionExecutions.get()).isEqualTo(1);
        assertThat(conflicts.get()).isGreaterThanOrEqualTo(1);
    }
}
