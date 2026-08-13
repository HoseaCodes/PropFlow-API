package com.hoseacodes.propflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * Covers the operational endpoints.
 *
 * <p>These are worth testing because they are what an orchestrator and an
 * on-call engineer depend on: a health probe that reports the wrong thing is
 * worse than none, since it makes an unhealthy instance look fine or takes a
 * healthy fleet out of rotation.
 */
class ActuatorIT extends AbstractIntegrationTest {

    @BeforeEach
    void reset() {
        resetDatabase();
    }

    @Test
    @DisplayName("health is reachable without a token")
    void healthIsPublic() throws Exception {
        // A load balancer cannot hold a credential, so this must be anonymous.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("anonymous health responses expose no component detail")
    void healthHidesComponentsFromAnonymousCallers() throws Exception {
        // UP or DOWN is everything a probe needs. Which component failed, and
        // the database vendor and version that come with it, are not for
        // unauthenticated callers.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    @DisplayName("an admin sees component detail")
    void adminSeesComponents() throws Exception {
        String admin = registerAdminAndSignIn("ops");

        mockMvc.perform(get("/actuator/health").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    @DisplayName("liveness and readiness are separate probes")
    void livenessAndReadinessProbesExist() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("readiness includes the database but liveness does not")
    void readinessIncludesDatabaseAndLivenessDoesNot() throws Exception {
        String admin = registerAdminAndSignIn("ops-groups");

        // This grouping is the point of the configuration. During a database
        // outage the instance should stop taking traffic (not ready) while
        // staying alive: a restart cannot fix the database, and restarting the
        // whole fleet turns a recoverable dependency failure into a cold-start
        // stampede when it returns.
        mockMvc.perform(get("/actuator/health/readiness")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db").exists());

        mockMvc.perform(get("/actuator/health/liveness")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db").doesNotExist());
    }

    @Test
    @DisplayName("metrics require an admin token")
    void prometheusRequiresAdmin() throws Exception {
        // The scrape output exposes request paths, timings, and JVM internals.
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());

        String user = registerAndSignIn("plain");
        mockMvc.perform(get("/actuator/prometheus").header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isForbidden());

        String admin = registerAdminAndSignIn("metrics-admin");
        mockMvc.perform(get("/actuator/prometheus").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("sensitive actuator endpoints are not exposed at all")
    void sensitiveEndpointsAreNotExposed() throws Exception {
        String admin = registerAdminAndSignIn("probe-admin");

        // Not merely protected -- absent. /actuator/env dumps configuration
        // including resolved property values, and heapdump can contain
        // credentials that passed through memory. Neither is something to
        // gate behind a role; they are removed from the exposure list.
        for (String endpoint : new String[]{
                "/actuator/env", "/actuator/beans", "/actuator/configprops",
                "/actuator/heapdump", "/actuator/threaddump", "/actuator/loggers",
                "/actuator/mappings"}) {

            mockMvc.perform(get(endpoint).header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isNotFound());
        }
    }
}
