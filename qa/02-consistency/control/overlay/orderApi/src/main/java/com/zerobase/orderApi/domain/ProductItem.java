package com.zerobase.orderApi.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.AuditOverride;
import org.hibernate.envers.Audited;

// =============================================================================
// [ADR-008 control 무방어 오버레이] 원하는 장애 ② 재고 초과판매 방어(재고 낙관적 락) 제거: @Version 삭제.
//   → 동시 재고 차감이 서로를 덮어써 oversell(음수 재고 / CONFIRMED > 초기재고) 발생.
//   product_item.version 컬럼은 DB(NOT NULL DEFAULT 0)에 남지만 엔티티가 매핑 안 함 → ddl-auto:validate OK.
//   빌드 중에만 원본 위에 덮어씀. 원본과 @Version 유무만 다르다: orderApi/.../domain/ProductItem.java
// =============================================================================
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Audited
@AuditOverride(forClass = BaseEntity.class)
public class ProductItem extends BaseEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long Id;

    private Long sellerId;
    private String name;
    private Integer price;
    private Integer count;

    // (control) @Version 제거 — 낙관적 락 없음.

    @ManyToOne
    @JoinColumn(name = "PRODUCT_ID")
    private Product product;

    public void setProduct(Product product)
    {
        if(this.product != null)
            this.product.getProductItemList().remove(this);

        this.product = product;

        if(this.product != null)
            this.product.getProductItemList().add(this);
    }
}
