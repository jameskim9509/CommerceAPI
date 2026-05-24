package com.zerobase.userApi.saga;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEvent.Pk> {
    boolean existsByEventIdAndConsumerName(UUID eventId, String consumerName);
}
