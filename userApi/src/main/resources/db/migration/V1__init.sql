-- =============================================================================
-- userApi V1: 메인 엔티티 테이블
--  - Spring Boot 3 / Hibernate 6 기본 네이밍(CamelCaseToUnderscoresNamingStrategy)
--    기준으로 컬럼명 결정 (예: verifyExpiredAt -> verify_expired_at).
--  - BaseEntity 의 createdDate / modifiedDate 는 JPA Auditing 으로 채워짐.
--  - Customer/Seller.roles 는 @ElementCollection(EAGER) 매핑 -> 별도
--    customer_roles / seller_roles 테이블로 분리.
-- =============================================================================

CREATE TABLE customer (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    email               VARCHAR(255),
    name                VARCHAR(255),
    password            VARCHAR(255),
    birth               DATE,
    phone_num           VARCHAR(255),
    verify_expired_at   DATETIME(6),
    verification_code   VARCHAR(255),
    verify              BIT(1)       NOT NULL DEFAULT b'0',
    balance             INT                   DEFAULT 0,
    created_date        DATETIME(6),
    modified_date       DATETIME(6),
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE seller (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    email               VARCHAR(255),
    name                VARCHAR(255),
    password            VARCHAR(255),
    birth               DATE,
    phone_num           VARCHAR(255),
    verify_expired_at   DATETIME(6),
    verification_code   VARCHAR(255),
    verify              BIT(1)       NOT NULL DEFAULT b'0',
    created_date        DATETIME(6),
    modified_date       DATETIME(6),
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE customer_balance_history (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    change_money    INT,
    current_money   INT,
    from_message    VARCHAR(255),
    description     VARCHAR(255),
    CUSTOMER_ID     BIGINT,
    created_date    DATETIME(6),
    modified_date   DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_cust_bal_hist_customer
        FOREIGN KEY (CUSTOMER_ID) REFERENCES customer (id)
) ENGINE = InnoDB;

-- @ElementCollection 으로 매핑된 Customer.roles / Seller.roles 컬렉션 테이블.
-- List<String> + @OrderColumn 미지정이라 bag 의미(중복 허용, 순서 X) -> PK 없음.
CREATE TABLE customer_roles (
    customer_id BIGINT       NOT NULL,
    roles       VARCHAR(255),
    CONSTRAINT fk_customer_roles_customer
        FOREIGN KEY (customer_id) REFERENCES customer (id)
) ENGINE = InnoDB;

CREATE TABLE seller_roles (
    seller_id BIGINT       NOT NULL,
    roles     VARCHAR(255),
    CONSTRAINT fk_seller_roles_seller
        FOREIGN KEY (seller_id) REFERENCES seller (id)
) ENGINE = InnoDB;
