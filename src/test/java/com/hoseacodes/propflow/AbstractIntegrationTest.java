package com.hoseacodes.propflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoseacodes.propflow.dto.request.SignInRequest;
import com.hoseacodes.propflow.dto.request.SignUpRequest;
import com.hoseacodes.propflow.model.Role;
import com.hoseacodes.propflow.model.User;
import com.hoseacodes.propflow.repository.UserRepository;

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

    @Autowired
    private UserRepository userRepository;

    protected static final String TEST_PASSWORD = "correct-horse-battery";

    /**
     * Registers an account and returns a usable {@code Authorization} header
     * value.
     *
     * <p>Deliberately goes through the real HTTP endpoints rather than seeding
     * a {@code SecurityContext} with {@code @WithMockUser}. A mocked principal
     * bypasses {@code JwtAuthenticationFilter} entirely, so the tests would
     * pass even if token parsing were broken -- which is precisely the code
     * these tests exist to cover.
     */
    protected String registerAndSignIn(String username) throws Exception {
        register(username);
        return bearerFor(username, TEST_PASSWORD);
    }

    /** Registers an account and promotes it to ADMIN before signing in. */
    protected String registerAdminAndSignIn(String username) throws Exception {
        register(username);
        User user = userRepository.findByUsernameIgnoringCase(username).orElseThrow();
        user.setRole(Role.ADMIN);
        userRepository.saveAndFlush(user);
        return bearerFor(username, TEST_PASSWORD);
    }

    protected void register(String username) throws Exception {
        var body = objectMapper.writeValueAsString(new SignUpRequest(
                username + "@example.com", username, TEST_PASSWORD, "Test", "User"));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    protected String bearerFor(String username, String password) throws Exception {
        var body = objectMapper.writeValueAsString(new SignInRequest(username, password));

        String response = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return "Bearer " + objectMapper.readTree(response).get("accessToken").asText();
    }
}
