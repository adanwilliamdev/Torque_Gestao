package com.torquegestao.repository;

import com.torquegestao.domain.Client;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    List<Client> findByActiveTrueOrderByCreatedAtDesc();

    Page<Client> findByActiveTrueAndNameContainingIgnoreCase(String name, Pageable pageable);

    @Query("select c from Client c where lower(c.name) like lower(concat('%', :name, '%')) order by c.name")
    List<Client> searchByName(@Param("name") String name);
}
