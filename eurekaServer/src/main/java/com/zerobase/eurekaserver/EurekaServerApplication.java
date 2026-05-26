package com.zerobase.eurekaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * ADR-005: 서비스 디스커버리 레지스트리.
 *  - gateway / orderApi / userApi 인스턴스가 자기 주소를 등록하고
 *    gateway 는 인스턴스 목록을 fetch 해 lb:// 라우팅에 사용한다.
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
