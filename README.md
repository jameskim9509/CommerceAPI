# CommerceAPI
판매 및 구매 API 개발

- ## 요구사항 ( REST API 구현 )
1. JWT 토큰을 활용한 사용자 회원 가입(with 이메일 검증), 로그인 기능 구현
3. 사용자별 판매자, 구매자 역할 구분
4. 판매자별 판매할 상품 관리 REST API 서버 개발
5. 구매자별 구매할 상품의 장바구니 관리 REST API 서버 개발
6. 구매자별 잔액 관리 및 장바구니를 통한 결제 시스템 개발
그외. 상품 필터링 기능 구현 등

* ## 프로젝트 구성도 <br> <br>
<img src = "/proj_structure.png" width="800" height="400">

- ## 기술 요구사항
1. **멀티 모듈** 프로젝트를 통해 한 곳에서 다양한 모듈을 관리할 수 있도록 하는 **마이크로서비스 아키텍처** 구현
( 프록시 서버, 사용자를 관리하는 USER API 서버 및 주문을 관리하는 ORDER API 서버로 분리된 마이크로서비스 아키텍처 )
2. **Spring Gateway**를 사용하여 USER API 서버 및 ORDER API에 접근할 수 있도록 하는 **프록시 서버** 구현
3. **Redis**를 활용하여 장바구니 Repository 구현
4. **JPA Spring Data**를 사용해 판매자 및 구매자 Repository 구현
( + **@EntityGraph**를 사용하여 즉시 로딩이 필요한 행에 대해 **fetch join** 수행 )
5. 사용자의 비밀번호를 **Spring Security**의 **Password Encoder**를 통해 암호화된 비밀번호로 저장
6. 운영 환경과 테스트 환경의 DB를 **MYSQL**서버와 **H2 Embeded**서버로 분리
7. 회원가입시 **mailgun**및 **open feign client**를 활용하여 **이메일 검증** 시스템 구현
8. 로그인 시 **Spring Security** 및 **JWT 토큰**을 활용하여 사용자 및 역할( 판매자, 구매자 )별 호출 url 제한
9. **다대일 양방향 매핑**을 사용하여 상품군과 상품 아이템 리스트의 연관관계 매핑 구현
10. **open feign client**를 사용하여 ORDER API에서 USER API로 결제 요청 및 결제 수행 기능 구현
11. **Query DSL**을 사용하여 동적 조건에 대한 상품 아이템 필터링 기능 구현
12. **@Query**를 활용하여 최근 순으로 결제 이력 조회
13. **BaseEntity** 및 **JPA Auditing**기능을 활용하여 CRUD 이력 관리
14. **Spring AOP** 기능을 활용해 **로깅 AOP** 구현, **custom error** 및 **error handler**를 통한 일관성 있는 예외 처리
15. **Mock Object** 통한 서비스별 단위 테스트 및 **SpringBootTest**를 통한 서비스, 컨트롤러 통합 테스트 구현
16. **swagger ui**를 통한 반응형 API 문서 제작
17. **docker** 및 **dockerfile**을 활용하여 도커 이미지 생성 자동화
18. **AWS** 및 **github action** 을 사용하여 **CI/CD** 자동화 구현

* ## Spring Boot 개발환경
  * Intellij IDE
  * 내장 tomcat
  * embeded h2 Database
  * MYSQL Database
  * QueryDSL
  * Redis server
  * Swagger
  * Docker
  * postman

* ## DB 테이블 <br> <br>
<img src = "/db_cap.png" width="600" height="200">

* ## Postman 사용 예
  * **이메일 검증을 통한 회원가입** <br> <br>
  <img src = "./postman_cap1(reqForReview).png" width="500" height="400"> <br> <br>
  * **로그인 시 JWT 토큰 발급** <br> <br>
  <img src = "./postman_cap2(getReview).png" width="500" height="400"> <br> <br>
  * **상품 등록 예**
  * **장바구니 관리 예**
  * **장바구니 결제**
    
* ## AWS 배포 현황
