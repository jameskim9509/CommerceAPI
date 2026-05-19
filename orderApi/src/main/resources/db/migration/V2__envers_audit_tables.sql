-- =============================================================================
-- orderApi V2: Hibernate Envers 감사 테이블
-- =============================================================================

CREATE TABLE revinfo (
    rev      INT    NOT NULL AUTO_INCREMENT,
    revtstmp BIGINT,
    PRIMARY KEY (rev)
) ENGINE = InnoDB;

CREATE TABLE product_aud (
    id              BIGINT  NOT NULL,
    rev             INT     NOT NULL,
    revtype         TINYINT,
    seller_id       BIGINT,
    name            VARCHAR(255),
    description     VARCHAR(255),
    created_date    DATETIME(6),
    modified_date   DATETIME(6),
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_product_aud_revinfo
        FOREIGN KEY (rev) REFERENCES revinfo (rev)
) ENGINE = InnoDB;

CREATE TABLE product_item_aud (
    id              BIGINT  NOT NULL,
    rev             INT     NOT NULL,
    revtype         TINYINT,
    seller_id       BIGINT,
    name            VARCHAR(255),
    price           INT,
    count           INT,
    PRODUCT_ID      BIGINT,
    created_date    DATETIME(6),
    modified_date   DATETIME(6),
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_product_item_aud_revinfo
        FOREIGN KEY (rev) REFERENCES revinfo (rev)
) ENGINE = InnoDB;
