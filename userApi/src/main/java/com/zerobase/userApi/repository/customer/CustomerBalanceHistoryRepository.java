package com.zerobase.userApi.repository.customer;

import com.zerobase.userApi.domain.customer.CustomerBalanceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerBalanceHistoryRepository extends JpaRepository<CustomerBalanceHistory, Long> {
    @Query("select h from CustomerBalanceHistory h " +
            "where h.id = (select max(h2.id) " +
                            "from CustomerBalanceHistory h2 join h2.customer c " +
                            "where c.id=:customer_id)")
    Optional<CustomerBalanceHistory> findByCustomerIdRecent(@Param("customer_id") Long customerId);
}
