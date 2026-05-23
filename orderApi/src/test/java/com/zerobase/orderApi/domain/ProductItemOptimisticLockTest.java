package com.zerobase.orderApi.domain;

import com.zerobase.orderApi.config.QueryDslConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(QueryDslConfig.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ProductItemOptimisticLockTest {

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("새 ProductItem 의 version 은 0 으로 초기화된다")
    void version_initialized_to_zero_on_insert() {
        ProductItem item = newItem();

        em.persist(item);
        em.flush();

        assertThat(item.getVersion()).isEqualTo(0L);
    }

    @Test
    @DisplayName("dirty checking 으로 ProductItem 을 변경하면 version 이 증가한다")
    void version_increments_on_update() {
        ProductItem item = newItem();
        em.persist(item);
        em.flush();

        item.setCount(5);
        em.flush();

        assertThat(item.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("다른 트랜잭션이 먼저 version 을 올린 뒤에 stale 한 in-memory 엔티티로 flush 하면 낙관적 락 충돌이 발생한다")
    void stale_version_triggers_optimistic_lock() {
        ProductItem item = newItem();
        em.persist(item);
        em.flush();
        Long id = item.getId();

        // 다른 트랜잭션의 커밋을 흉내내어 DB row 의 version 만 +1
        em.createNativeQuery(
                        "UPDATE product_item SET version = version + 1, count = 5 WHERE id = ?1")
                .setParameter(1, id)
                .executeUpdate();

        // 영속성 컨텍스트의 엔티티는 여전히 version=0 (stale)
        item.setCount(3);

        // flush 시 UPDATE ... WHERE version=0 → 0 rows → 낙관적 락 충돌
        assertThatThrownBy(em::flush)
                .isInstanceOfAny(
                        OptimisticLockException.class,
                        ObjectOptimisticLockingFailureException.class);
    }

    private ProductItem newItem() {
        return ProductItem.builder()
                .sellerId(1L)
                .name("test-item")
                .price(1000)
                .count(10)
                .build();
    }
}
