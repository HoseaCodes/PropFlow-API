package com.hoseacodes.propflow;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Base class for integration tests that exercise the full stack:
 * controller -> service -> repository -> PostgreSQL.
 *
 * <h2>Why a real PostgreSQL instead of H2</h2>
 * An in-memory database in "PostgreSQL compatibility mode" is not PostgreSQL.
 * It diverges on type coercion, constraint and index semantics, sequence
 * behaviour, {@code NUMERIC} precision, upsert syntax, and JSON support. A test
 * that passes against H2 is not evidence about the database this application
 * actually deploys on, and the difference tends to surface exactly where it
 * matters -- in constraint violations and migration behaviour. The cost is
 * requiring Docker and a slower first test; that is a good trade.
 *
 * <h2>Why the container is a JVM-wide singleton</h2>
 * The container is started once in a static initialiser rather than managed by
 * the {@code @Testcontainers} JUnit extension. The extension starts and stops a
 * static container per test <em>class</em>, so every additional integration
 * test class would pay the full PostgreSQL startup cost again. Started here,
 * one container serves the entire test run and Testcontainers' Ryuk sidecar
 * removes it when the JVM exits.
 *
 * <p>This pairs with Spring's test context caching: because every subclass
 * declares an identical context configuration, the application context is built
 * once and reused too.
 *
 * <p>{@code @ServiceConnection} wires the container's JDBC URL, username, and
 * password into the datasource automatically, so no test needs to know the
 * randomly assigned port. Flyway then migrates the fresh database on first
 * context startup, which means every run is continuous proof that the
 * migrations apply cleanly from nothing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"));

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;
}
