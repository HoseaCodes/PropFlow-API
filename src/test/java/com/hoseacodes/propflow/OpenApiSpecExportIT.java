package com.hoseacodes.propflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Verifies the generated OpenAPI document, and writes it to
 * {@code target/openapi.json} for the published API reference.
 *
 * <h2>Why a test rather than a Maven plugin</h2>
 * {@code springdoc-openapi-maven-plugin} generates the spec by starting the
 * packaged application and calling it, which means provisioning a database and
 * a signing key inside the build. This test already has both — the
 * Testcontainers PostgreSQL and the test profile — so exporting here costs
 * nothing extra.
 *
 * <p>More importantly it makes the export <em>self-verifying</em>. The file is
 * only produced if the assertions below hold, so a published reference cannot
 * silently become empty or lose its security scheme. A plugin would happily
 * write whatever the application returned, including nothing.
 *
 * <p>The previous dependency, {@code springdoc-openapi-ui} 1.7.0, was a Spring
 * Boot 2 artifact whose autoconfiguration never activated on Boot 3 — so the
 * documentation endpoints returned nothing at all and nobody noticed. These
 * assertions exist so that cannot recur unnoticed.
 */
class OpenApiSpecExportIT extends AbstractIntegrationTest {

    private static final Path OUTPUT = Path.of("target", "openapi.json");

    @Test
    @DisplayName("the OpenAPI document is complete, and is exported for publishing")
    void exportsAndValidatesSpec() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode spec = objectMapper.readTree(json);

        assertThat(spec.path("openapi").asText()).startsWith("3.");
        assertThat(spec.path("info").path("title").asText()).isEqualTo("PropFlow API");

        // The bearer scheme is what makes the published UI usable rather than
        // merely present: without it there is no Authorize control and every
        // protected endpoint reads as broken.
        assertThat(spec.path("components").path("securitySchemes").path("bearerAuth").path("scheme")
                .asText()).isEqualTo("bearer");

        JsonNode paths = spec.path("paths");
        assertThat(paths.size())
                .as("documented paths")
                .isGreaterThanOrEqualTo(10);

        // Spot-check that the real resources are present, so a routing change
        // that silently drops a controller from the document is caught.
        assertThat(paths.has("/api/auth/signin")).isTrue();
        assertThat(paths.has("/api/properties")).isTrue();
        assertThat(paths.has("/api/transactions")).isTrue();
        assertThat(paths.has("/api/users/me")).isTrue();

        // The auth endpoints clear the document-wide bearer requirement; if they
        // did not, Swagger UI would attach a token to the call that issues one.
        assertThat(paths.path("/api/auth/signin").path("post").path("security"))
                .as("sign-in must not require a bearer token")
                .isEmpty();

        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(spec));

        assertThat(OUTPUT).exists();
    }
}
