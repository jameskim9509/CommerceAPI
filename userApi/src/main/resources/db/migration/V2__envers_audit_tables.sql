-- =============================================================================
-- userApi V2: Hibernate Envers 감사(audit) 테이블
--  - 모든 @Audited 엔티티마다 *_aud 테이블 필요
--  - 공통 REVINFO 테이블이 revision 정보를 보관
--  - @AuditOverride(forClass = BaseEntity.class) 가 명시돼 있어
--    createdDate / modifiedDate 도 audit 대상에 포함됨
-- =============================================================================

CREATE TABLE revinfo (
    rev      INT    NOT NULL AUTO_INCREMENT,
    revtstmp BIGINT,
    PRIMARY KEY (rev)
) ENGINE = InnoDB;

CREATE TABLE customer_aud (
    id                  BIGINT  NOT NULL,
    rev                 INT     NOT NULL,
    revtype             TINYINT,
    email               VARCHAR(255),
    name                VARCHAR(255),
    password            VARCHAR(255),
    birth               DATE,
    phone_num           VARCHAR(255),
    verify_expired_at   DATETIME(6),
    verification_code   VARCHAR(255),
    verify              BIT(1),
    balance             INT,
    created_date        DATETIME(6),
    modified_date       DATETIME(6),
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_customer_aud_revinfo
        FOREIGN KEY (rev) REFERENCES revinfo (rev)
) ENGINE = InnoDB;

CREATE TABLE seller_aud (
    id                  BIGINT  NOT NULL,
    rev                 INT     NOT NULL,
    revtype             TINYINT,
    email               VARCHAR(255),
    name                VARCHAR(255),
    password            VARCHAR(255),
    birth               DATE,
    phone_num           VARCHAR(255),
    verify_expired_at   DATETIME(6),
    verification_code   VARCHAR(255),
    verify              BIT(1),
    created_date        DATETIME(6),
    modified_date       DATETIME(6),
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_seller_aud_revinfo
        FOREIGN KEY (rev) REFERENCES revinfo (rev)
) ENGINE = InnoDB;

CREATE TABLE customer_balance_history_aud (
    id              BIGINT  NOT NULL,
    rev             INT     NOT NULL,
    revtype         TINYINT,
    change_money    INT,
    current_money   INT,
    from_message    VARCHAR(255),
    description     VARCHAR(255),
    CUSTOMER_ID     BIGINT,
    created_date    DATETIME(6),
    modified_date   DATETIME(6),
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_cust_bal_hist_aud_revinfo
        FOREIGN KEY (rev) REFERENCES revinfo (rev)
) ENGINE = InnoDB;

-- @ElementCollection (Customer.roles, Seller.roles) 의 audit 테이블.
-- Envers 는 (rev, FK, value) 조합을 PK 로 사용.
CREATE TABLE customer_roles_aud (
    rev         INT          NOT NULL,
    revtype     TINYINT,
    customer_id BIGINT       NOT NULL,
    roles       VARCHAR(255) NOT NULL,
    PRIMARY KEY (rev, customer_id, roles),
    CONSTRAINT fk_customer_roles_aud_revinfo
        FOREIGN KEY (rev) REFERENCES revinfo (rev)
) ENGINE = InnoDB;

CREATE TABLE seller_roles_aud (
    rev       INT          NOT NULL,
    revtype   TINYINT,
    seller_id BIGINT       NOT NULL,
    roles     VARCHAR(255) NOT NULL,
    PRIMARY KEY (rev, seller_id, roles),
    CONSTRAINT fk_seller_roles_aud_revinfo
        FOREIGN KEY (rev) REFERENCES revinfo (rev)
) ENGINE = InnoDB;
