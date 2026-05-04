package cn.kuship.console.modules.openapi.docs;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Springdoc OpenAPI 3 wiring for the {@code /openapi/v1/**} endpoints.
 *
 * <p>Only enabled when {@code kuship.openapi.docs.enabled=true} (dev / local default true,
 * prod default false). The bean wiring auto-generates the OpenAPI 3 JSON document from the
 * REST controllers under {@code cn.kuship.console.modules.openapi.v1.**} and exposes it at
 * {@code /openapi/v3/api-docs}; Swagger UI sits at {@code /openapi/swagger-ui/index.html}.
 *
 * <p>Two security schemes are declared so users can Try-It-Out from the UI:
 * <ul>
 *   <li>{@code InternalToken} — apiKey in header {@code X-Internal-Token} (matches OpenApiAuthFilter)</li>
 *   <li>{@code BearerAuth} — http bearer scheme using a personal access token from {@code user_access_key}</li>
 * </ul>
 *
 * <p>Each operation becomes a candidate for either scheme; Springdoc renders an "Authorize"
 * button in the UI letting the user pick one. The {@code /console/**} controllers are NOT
 * scanned (BFF endpoints are not part of the published API).
 */
@Configuration
@ConditionalOnProperty(name = "kuship.openapi.docs.enabled", havingValue = "true")
public class SpringDocConfig {

    private static final Logger log = LoggerFactory.getLogger(SpringDocConfig.class);

    private static final String SCHEME_INTERNAL_TOKEN = "InternalToken";
    private static final String SCHEME_BEARER_AUTH = "BearerAuth";

    @Bean
    public OpenAPI kuShipOpenAPI() {
        log.info("Springdoc OpenAPI 3 endpoint registered at /openapi/v3/api-docs");
        return new OpenAPI()
                .info(new Info()
                        .title("Kuship OpenAPI v1")
                        .version("1.0")
                        .description("Public REST API for grctl CLI, third-party integrations and "
                                + "GitOps pipelines. Authenticate with X-Internal-Token (internal "
                                + "service-to-service) or Authorization: Bearer <PAT> (admin-tier "
                                + "personal access token). Responses use the {detail, code} shape; "
                                + "HTTP status code mirrors the business code.")
                        .license(new License().name("Apache-2.0").url("https://www.apache.org/licenses/LICENSE-2.0"))
                        .contact(new Contact().name("kuship-console").url("https://github.com/")))
                .servers(List.of(new Server().url("/").description("Current host")))
                .components(new Components()
                        .addSecuritySchemes(SCHEME_INTERNAL_TOKEN,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-Internal-Token")
                                        .description("Internal service-to-service token; matches "
                                                + "kuship.openapi.internal-token (env INTERNAL_API_TOKEN)."))
                        .addSecuritySchemes(SCHEME_BEARER_AUTH,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .description("Personal access token from user_access_key "
                                                + "(only sys_admin tokens are accepted).")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_INTERNAL_TOKEN))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_BEARER_AUTH));
    }

    @Bean
    public GroupedOpenApi v1Group() {
        return GroupedOpenApi.builder()
                .group("v1")
                .pathsToMatch("/openapi/v1/**")
                .packagesToScan("cn.kuship.console.modules.openapi.v1")
                .build();
    }

}
