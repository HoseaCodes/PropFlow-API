package com.hoseacodes.propflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.hoseacodes.propflow.dto.request.SignInRequest;
import com.hoseacodes.propflow.dto.request.SignUpRequest;
import com.hoseacodes.propflow.model.User;
import com.hoseacodes.propflow.repository.UserRepository;

/**
 * End-to-end coverage of registration, sign-in, and endpoint protection.
 *
 * <p>Before this work the entire API was reachable anonymously, sign-in issued
 * nothing the client could present on a later request, and one registration
 * path stored passwords in plaintext. These tests exist so none of that can
 * silently return.
 */
class AuthApiIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetUsers() {
        resetDatabase();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /** Strips the timestamp so two error bodies can be compared for equality. */
    private String withoutTimestamp(String body) throws Exception {
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(body);
        node.remove("timestamp");
        return node.toString();
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("registration")
    class Registration {

        @Test
        @DisplayName("returns 201 with a Location header and no credentials in the body")
        void registersSuccessfully() throws Exception {
            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new SignUpRequest(
                                    "ada@example.com", "ada", TEST_PASSWORD, "Ada", "Lovelace"))))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.email").value("ada@example.com"))
                    .andExpect(jsonPath("$.role").value("USER"))
                    // The regression this guards: the endpoint used to return
                    // the entity, publishing the BCrypt hash to the caller.
                    .andExpect(jsonPath("$.password").doesNotExist());
        }

        @Test
        @DisplayName("stores the password as a BCrypt hash, never as plaintext")
        void hashesPassword() throws Exception {
            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new SignUpRequest(
                                    "grace@example.com", "grace", TEST_PASSWORD, null, null))))
                    .andExpect(status().isCreated());

            User stored = userRepository.findByUsernameIgnoringCase("grace").orElseThrow();

            assertThat(stored.getPassword()).isNotEqualTo(TEST_PASSWORD);
            assertThat(stored.getPassword()).startsWith("$2");
            assertThat(passwordEncoder.matches(TEST_PASSWORD, stored.getPassword())).isTrue();
        }

        @Test
        @DisplayName("assigns the USER role regardless of client input")
        void cannotSelfAssignAdmin() throws Exception {
            // An extra "role" field is simply not part of SignUpRequest, so
            // Jackson has nowhere to bind it. This is why the accepted shape
            // matters more than post-binding sanitisation.
            String payloadWithRole = """
                    {"email":"mallory@example.com","username":"mallory",
                     "password":"%s","firstName":"M","lastName":"X","role":"ADMIN"}
                    """.formatted(TEST_PASSWORD);

            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payloadWithRole))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role").value("USER"));

            assertThat(userRepository.findByUsernameIgnoringCase("mallory").orElseThrow()
                    .getRole().name()).isEqualTo("USER");
        }

        @Test
        @DisplayName("rejects a duplicate email with 409")
        void rejectsDuplicateEmail() throws Exception {
            register("alan");

            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new SignUpRequest(
                                    "alan@example.com", "different", TEST_PASSWORD, null, null))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Conflict"));
        }

        @Test
        @DisplayName("treats emails differing only by case as duplicates")
        void rejectsCaseVariantEmail() throws Exception {
            register("edsger");

            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new SignUpRequest(
                                    "EDSGER@example.com", "other", TEST_PASSWORD, null, null))))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("rejects an invalid payload with 400 and per-field messages")
        void rejectsInvalidPayload() throws Exception {
            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new SignUpRequest(
                                    "not-an-email", "x", "short", null, null))))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.email").exists())
                    .andExpect(jsonPath("$.errors.username").exists())
                    .andExpect(jsonPath("$.errors.password").exists());
        }

        @Test
        @DisplayName("rejects malformed JSON with 400 and no internal detail")
        void rejectsMalformedJson() throws Exception {
            String body = mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": "))
                    .andExpect(status().isBadRequest())
                    .andReturn().getResponse().getContentAsString();

            assertThat(body)
                    .doesNotContain("com.fasterxml")
                    .doesNotContain("Exception");
        }
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("sign-in")
    class SignIn {

        @Test
        @DisplayName("returns a bearer token and the user, without credentials")
        void issuesToken() throws Exception {
            register("linus");

            mockMvc.perform(post("/api/auth/signin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new SignInRequest("linus", TEST_PASSWORD))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").isNumber())
                    .andExpect(jsonPath("$.user.username").value("linus"))
                    .andExpect(jsonPath("$.user.password").doesNotExist());
        }

        @Test
        @DisplayName("accepts a username differing only by case")
        void usernameIsCaseInsensitive() throws Exception {
            register("barbara");

            mockMvc.perform(post("/api/auth/signin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new SignInRequest("BARBARA", TEST_PASSWORD))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("rejects a wrong password with 401")
        void rejectsWrongPassword() throws Exception {
            register("katherine");

            mockMvc.perform(post("/api/auth/signin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new SignInRequest("katherine", "wrong-password"))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("gives an unknown user the same response as a wrong password")
        void doesNotRevealWhetherAccountExists() throws Exception {
            register("known");

            String unknownUser = mockMvc.perform(post("/api/auth/signin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new SignInRequest("nobody", "wrong-password"))))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();

            String wrongPassword = mockMvc.perform(post("/api/auth/signin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new SignInRequest("known", "wrong-password"))))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();

            // Identical apart from the timestamp, which necessarily differs
            // between two requests. Any other difference -- a distinct message,
            // status, or field -- would let an attacker enumerate which
            // usernames are registered.
            assertThat(withoutTimestamp(unknownUser)).isEqualTo(withoutTimestamp(wrongPassword));
        }
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("endpoint protection")
    class EndpointProtection {

        @Test
        @DisplayName("protected endpoints reject anonymous requests with 401")
        void anonymousRequestsAreRejected() throws Exception {
            // The headline regression. Every one of these was previously
            // reachable with no credentials at all.
            for (String path : new String[]{
                    "/api/properties", "/api/transactions", "/api/users", "/api/users/me"}) {
                mockMvc.perform(get(path))
                        .andExpect(status().isUnauthorized())
                        .andExpect(content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON))
                        .andExpect(jsonPath("$.title").value("Authentication required"));
            }
        }

        @Test
        @DisplayName("a valid token grants access")
        void validTokenGrantsAccess() throws Exception {
            String token = registerAndSignIn("valid-user");

            mockMvc.perform(get("/api/properties").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("a garbage token is rejected with 401")
        void garbageTokenRejected() throws Exception {
            mockMvc.perform(get("/api/properties")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("a token signed with the wrong key is rejected with 401")
        void forgedTokenRejected() throws Exception {
            // Minted with a different secret than the application uses.
            var forger = new com.hoseacodes.propflow.security.JwtService(
                    new com.hoseacodes.propflow.security.JwtProperties(
                            "an-entirely-different-key-of-sufficient-length-00",
                            java.time.Duration.ofHours(1)));
            String forged = forger.generateToken(
                    org.springframework.security.core.userdetails.User
                            .withUsername("attacker").password("x").roles("ADMIN").build());

            mockMvc.perform(get("/api/properties")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("a token for a deleted account stops working immediately")
        void tokenForDeletedUserIsRejected() throws Exception {
            String token = registerAndSignIn("ephemeral");

            mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk());

            resetDatabase();

            // The token is still cryptographically valid and unexpired. It stops
            // working only because the filter reloads the principal from the
            // database on every request -- the deliberate trade of a query per
            // request for immediate effect of account changes.
            mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("auth endpoints stay public")
        void authEndpointsArePublic() throws Exception {
            mockMvc.perform(post("/api/auth/signin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new SignInRequest("nobody", "nothing"))))
                    .andExpect(status().isUnauthorized()); // reached the handler, not the filter

            mockMvc.perform(post("/api/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new SignUpRequest(
                                    "public@example.com", "publicuser", TEST_PASSWORD, null, null))))
                    .andExpect(status().isCreated());
        }
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("role-based authorization")
    class RoleAuthorization {

        @Test
        @DisplayName("a standard user cannot list all users")
        void standardUserCannotListUsers() throws Exception {
            String token = registerAndSignIn("plain-user");

            mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.title").value("Access denied"));
        }

        @Test
        @DisplayName("an admin can list all users, and no hash is exposed")
        void adminCanListUsers() throws Exception {
            String token = registerAdminAndSignIn("root");

            mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].username").exists())
                    // GET /api/users previously returned every account's BCrypt
                    // hash, to anyone, with no authentication.
                    .andExpect(jsonPath("$[0].password").doesNotExist());
        }

        @Test
        @DisplayName("any authenticated user can read their own profile")
        void anyUserCanReadOwnProfile() throws Exception {
            String token = registerAndSignIn("selfie");

            mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("selfie"))
                    .andExpect(jsonPath("$.password").doesNotExist());
        }

        @Test
        @DisplayName("the removed plaintext-password and mass-assignment endpoints are gone")
        void removedEndpointsNoLongerExist() throws Exception {
            String token = registerAdminAndSignIn("admin2");

            // POST /api/users stored the password unhashed.
            mockMvc.perform(post("/api/users")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"x@example.com\",\"username\":\"x\",\"password\":\"plaintext\"}"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }
}
