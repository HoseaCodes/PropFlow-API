package com.hoseacodes.propflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * OpenAPI document metadata and the bearer-token security scheme.
 *
 * <p>Declaring the scheme is what makes the generated Swagger UI usable rather
 * than merely present: it adds the Authorize button, so a reviewer can sign in
 * at {@code /api/auth/signin}, paste the token once, and exercise every
 * protected endpoint from the browser. Without it the UI renders the endpoints
 * but every call returns 401, which looks like a broken API.
 *
 * <p>The spec is generated from the controllers and the validation annotations
 * on the request records, so it cannot drift from the code the way
 * hand-maintained endpoint documentation does. That is the reason API details
 * are not duplicated at length in the README.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI propflowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PropFlow API")
                        .version("v1")
                        .description("""
                                REST API for managing short-term rental properties and their \
                                financial transactions.

                                **Authentication.** Register at `POST /api/auth/signup`, then \
                                exchange credentials for a token at `POST /api/auth/signin`. \
                                Click **Authorize** and paste the `accessToken` value.

                                **Authorization.** Every property and transaction read is scoped \
                                to the authenticated owner. Requesting a resource belonging to \
                                another account returns `404` rather than `403`, so that response \
                                codes cannot be used to discover which ids exist.

                                **Errors.** All failures are RFC 7807 \
                                `application/problem+json`, carrying `type`, `title`, `status`, \
                                `detail`, `instance`, and `timestamp`. Validation failures add an \
                                `errors` object keyed by field name. Unexpected failures add a \
                                `correlationId` that matches the server log entry; stack traces \
                                and SQL are never returned.

                                This is a portfolio project. It is not deployed and not \
                                production-hardened -- see the repository README for the current \
                                list of known limitations.""")
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the accessToken returned by /api/auth/signin.")))
                // Applied document-wide because the security chain denies by
                // default; the two auth endpoints are the exception and are
                // annotated individually.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
