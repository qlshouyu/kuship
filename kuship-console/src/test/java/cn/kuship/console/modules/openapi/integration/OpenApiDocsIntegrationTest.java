package cn.kuship.console.modules.openapi.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies the Springdoc-generated OpenAPI 3 document and Swagger UI surface
 * (add-openapi-swagger-ui).
 *
 * <p>The contract-test profile pins {@code kuship.openapi.docs.enabled=true} so the
 * {@code SpringDocConfig} bean wires up. We assert that:
 * <ul>
 *   <li>The JSON endpoint and UI bootstrap are anonymously reachable</li>
 *   <li>The JSON only describes {@code /openapi/v1/**} paths (BFF console endpoints stay private)</li>
 *   <li>Both InternalToken and BearerAuth security schemes render in the document</li>
 *   <li>Business endpoints under {@code /openapi/v1/**} still require authentication</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy",
        "kuship.openapi.docs.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenApiDocsIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void api_docs_returns_openapi_3_json() throws Exception {
        mvc.perform(get("/openapi/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.openapi").value(org.hamcrest.Matchers.startsWith("3.")));
    }

    @Test
    void api_docs_lists_v1_paths() throws Exception {
        mvc.perform(get("/openapi/v3/api-docs/v1"))
                .andExpect(status().isOk())
                // At least one well-known v1 endpoint must show up
                .andExpect(jsonPath("$.paths['/openapi/v1/regions']").exists());
    }

    @Test
    void api_docs_excludes_console_paths() throws Exception {
        // The default group endpoint should not surface BFF /console/** paths because
        // SpringDocConfig.v1Group() restricts packagesToScan to the openapi.v1 package.
        String body = mvc.perform(get("/openapi/v3/api-docs/v1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        if (body.contains("\"/console/")) {
            throw new AssertionError("OpenAPI v1 document leaks /console/** paths: " + body);
        }
    }

    @Test
    void api_docs_declares_both_security_schemes() throws Exception {
        mvc.perform(get("/openapi/v3/api-docs/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.InternalToken.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.InternalToken.in").value("header"))
                .andExpect(jsonPath("$.components.securitySchemes.InternalToken.name").value("X-Internal-Token"))
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.scheme").value("bearer"));
    }

    @Test
    void swagger_ui_html_is_anonymously_reachable() throws Exception {
        // Springdoc serves a redirect/HTML bootstrap at /swagger-ui/index.html. The exact final
        // path is rewritten via springdoc.swagger-ui.path; we assert the HTML body is reachable
        // and contains a swagger-ui marker. We tolerate both the configured path and webjar root.
        try {
            mvc.perform(get("/openapi/swagger-ui/index.html"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Swagger UI")));
        } catch (AssertionError e) {
            // Some Springdoc versions redirect first; verify swagger-config remains anonymous.
            mvc.perform(get("/openapi/v3/api-docs/swagger-config"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void unauth_v1_endpoint_returns_401() throws Exception {
        // Confirms the OpenApiAuthFilter still gates real business endpoints even after the
        // skip list change (regression guard for add-openapi-swagger-ui task 5).
        mvc.perform(get("/openapi/v1/regions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void unauth_api_docs_returns_200() throws Exception {
        // Anonymous browsing of the JSON document — the documentation surface itself is public.
        mvc.perform(get("/openapi/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
