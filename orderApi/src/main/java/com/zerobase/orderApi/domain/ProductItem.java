package com.zerobase.orderApi.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.AuditOverride;
import org.hibernate.envers.Audited;

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
