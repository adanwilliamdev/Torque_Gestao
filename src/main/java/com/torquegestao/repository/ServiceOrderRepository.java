package com.torquegestao.repository;

import com.torquegestao.domain.ServiceOrder;
import com.torquegestao.domain.ServiceOrderStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, UUID> {

    @EntityGraph(attributePaths = {"client", "vehicle", "mechanic", "items", "items.part"})
    Optional<ServiceOrder> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"client", "vehicle", "mechanic", "items", "items.part"})
    List<ServiceOrder> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"client", "vehicle", "mechanic", "items", "items.part"})
    List<ServiceOrder> findByMechanicIdOrderByCreatedAtDesc(UUID mechanicId);

    @EntityGraph(attributePaths = {"client", "vehicle", "mechanic", "items", "items.part"})
    List<ServiceOrder> findByClientIdOrderByCreatedAtDesc(UUID clientId);

    @EntityGraph(attributePaths = {"client", "vehicle", "mechanic", "items", "items.part"})
    List<ServiceOrder> findByVehicleIdOrderByCreatedAtDesc(UUID vehicleId);

    @EntityGraph(attributePaths = {"client", "vehicle", "mechanic", "items", "items.part"})
    List<ServiceOrder> findByStatusOrderByCreatedAtDesc(ServiceOrderStatus status);

    /** Página sem coleções no fetch (paginar com JOIN FETCH de coleção pagina em memória); itens vêm em lote. */
    @EntityGraph(attributePaths = {"client", "vehicle", "mechanic"})
    Page<ServiceOrder> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"client", "vehicle", "mechanic"})
    Page<ServiceOrder> findByStatus(ServiceOrderStatus status, Pageable pageable);

    long countByStatusIn(Collection<ServiceOrderStatus> statuses);

    /** Pares (completedAt, totalAmount) das OS finalizadas desde {@code since}. */
    @Query("""
            select o.completedAt, o.totalAmount
            from ServiceOrder o
            where o.status = com.torquegestao.domain.ServiceOrderStatus.FINALIZADA
              and o.completedAt >= :since
            """)
    List<Object[]> completedRevenueSince(@Param("since") Instant since);

    @Query(value = "select nextval('service_order_number_seq')", nativeQuery = true)
    long nextOrderSequence();

    @Query("""
            select m.id, m.name, count(o)
            from ServiceOrder o join o.mechanic m
            where o.status = com.torquegestao.domain.ServiceOrderStatus.FINALIZADA
            group by m.id, m.name
            order by count(o) desc, m.name asc
            """)
    List<Object[]> topMechanics(Pageable pageable);

    @Query("""
            select p.id, p.name, sum(i.quantity)
            from ServiceOrderItem i join i.part p
            group by p.id, p.name
            order by sum(i.quantity) desc, p.name asc
            """)
    List<Object[]> mostUsedParts(Pageable pageable);
}
