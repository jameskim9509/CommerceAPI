-- =============================================================================
-- orderApi V1: 메인 엔티티 테이블
--  - Cart 는 Redis 에 저장(@RedisHash)되므로 RDB 테이블이 없음
--  - ProductItem.@JoinColumn(name = "PRODUCT_ID") 는 대문자 컬럼명을 명시
-- =============================================================================

CREATE TABLE product (
    id              BIGINT  NOT NULL AUTO_INCREMENT,
    seller_id       BIGINT,
    name            VARCHAR(255),
    description     VARCHAR(255),
    created_date    DATETIME(6),
    modified_date   DATETIME(6),
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE product_item (
    id              BIGINT  NOT NULL AUTO_INCREMENT,
    seller_id       BIGINT,
    name            VARCHAR(255),
    price           INT,
    count           INT,
    PRODUCT_ID      BIGINT,
    created_date    DATETIME(6),
    modified_date   DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_product_item_product
        FOREIGN KEY (PRODUCT_ID) REFERENCES product (id)
) ENGINE = InnoDB;
