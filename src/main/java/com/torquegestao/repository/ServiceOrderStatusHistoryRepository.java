package com.torquegestao.repository;

import com.torquegestao.domain.ServiceOrderStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceOrderStatusHistoryRepository extends JpaRepository<ServiceOrderStatusHistory, UUID> {

    @EntityGraph(attributePaths = "changedBy")
    List<ServiceOrderStatusHistory> findByServiceOrderIdOrderByChangedAtAsc(UUID serviceOrderId);
}
