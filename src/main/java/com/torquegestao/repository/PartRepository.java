package com.torquegestao.repository;

import com.torquegestao.domain.Part;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PartRepository extends JpaRepository<Part, UUID> {

    List<Part> findByActiveTrueOrderByCreatedAtDesc();

    Page<Part> findByActiveTrueAndNameContainingIgnoreCase(String name, Pageable pageable);

    boolean existsByCode(String code);

    @Query("select p from Part p where p.active = true and p.stockQuantity <= p.minimumStock order by p.name")
    List<Part> findLowStock();

    @Query("select count(p) from Part p where p.active = true and p.stockQuantity <= p.minimumStock")
    long countLowStock();
}
