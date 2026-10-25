package com.torquegestao;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Sobe a aplicação completa contra um PostgreSQL real (Testcontainers). Requer Docker.
 *
 * <p>O container é um singleton, iniciado uma única vez para toda a execução. Com {@code @Container}
 * (um por classe de teste) o Spring reaproveitava o contexto em cache, que continuava apontando
 * para o container da primeira classe, já encerrado, e as demais classes perdiam a conexão com o
 * banco. O Ryuk do Testcontainers remove o container quando a JVM termina.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
