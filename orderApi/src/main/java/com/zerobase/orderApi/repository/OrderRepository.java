package com.zerobase.orderApi.repository;

import com.zerobase.orderApi.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
