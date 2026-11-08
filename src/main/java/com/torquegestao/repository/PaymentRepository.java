package com.torquegestao.repository;

import com.torquegestao.domain.Payment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @EntityGraph(attributePaths = "createdBy")
    List<Payment> findByServiceOrderIdOrderByPaidAtAsc(UUID serviceOrderId);
}
