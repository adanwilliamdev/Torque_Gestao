package com.torquegestao.repository;

import com.torquegestao.domain.Budget;
import com.torquegestao.domain.BudgetStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    @EntityGraph(attributePaths = {"client", "vehicle"})
    Optional<Budget> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"client", "vehicle"})
    List<Budget> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"client", "vehicle"})
    List<Budget> findByClientIdOrderByCreatedAtDesc(UUID clientId);

    @EntityGraph(attributePaths = {"client", "vehicle"})
    List<Budget> findByStatusOrderByCreatedAtDesc(BudgetStatus status);

    /** Existe orçamento da OS em algum dos status informados (usado para barrar um segundo orçamento ativo). */
    boolean existsByServiceOrderIdAndStatusIn(UUID serviceOrderId, Collection<BudgetStatus> statuses);

    /** Marca como EXPIRADO todo orçamento pendente cuja validade já passou. Devolve quantos mudaram. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Budget b
            set b.status = com.torquegestao.domain.BudgetStatus.EXPIRADO, b.updatedAt = :now
            where b.status = com.torquegestao.domain.BudgetStatus.PENDENTE
              and b.validUntil is not null
              and b.validUntil < :now
            """)
    int expireOverdue(@Param("now") Instant now);

    @Query(value = "select nextval('budget_number_seq')", nativeQuery = true)
    long nextBudgetSequence();
}
