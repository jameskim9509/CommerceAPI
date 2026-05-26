package com.zerobase.orderApi.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 양방향 매핑: order_id NOT NULL 충돌 회피.
    // (이전 @OneToMany @JoinColumn 단방향은 Hibernate 6 에서 insert-then-update 로 동작해
    //  최초 INSERT 시 order_id 가 null 이 되어 NOT NULL 위반 발생.)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    private Long productItemId;
    private String name;
    private Integer count;
    private Integer price;
}
