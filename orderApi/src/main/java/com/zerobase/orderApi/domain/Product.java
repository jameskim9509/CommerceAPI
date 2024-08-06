package com.zerobase.orderApi.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.AuditOverride;
import org.hibernate.envers.Audited;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Audited
@AuditOverride(forClass = BaseEntity.class)
public class Product extends BaseEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sellerId;
    private String name;
    private String description;

    @Column(name = "PRODUCT_ITEMS")
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "product")
    private List<ProductItem> productItemList;

    public void addProductItem(ProductItem item)
    {
        item.setProduct(this);
    }
}
