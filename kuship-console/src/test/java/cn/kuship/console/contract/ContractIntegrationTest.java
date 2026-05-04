package cn.kuship.console.contract;

import cn.kuship.console.common.security.JwtClaims;
import cn.kuship.console.common.security.JwtTokenService;
import cn.kuship.console.common.trace.TraceIdFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 契约层端到端集成测试：用 ContractDemoController 走完 5 类返回 + 5 类异常 + JWT + 分页 + traceId 主路径。
 *
 * <p>profile {@code local} + {@code contract-test}：连本机真实 MySQL（与 application-local.yaml 一致），
 * 启用 demo controller。本测试不读写任何业务表，与共享 schema 兼容。
 *
 * <p>用 {@code @TestPropertySource} 行内覆盖 secret-key，确保独立于 dev key。
 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContractIntegrationTest {

    /**
     * 用一个不太可能与真实用户冲突的高位 user_id 作为测试 fixture。
     * migrate-console-account-team 起 JwtAuthFilter 要求 token 中的 user_id 必须真实存在于 user_info 表。
     */
    private static final int TEST_USER_ID = 909042;

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JwtTokenService tokenService;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeAll
    void seedTestUser() {
        // upsert：保证 user_id=909042 在 user_info 表存在；测试结束清理
        jdbcTemplate.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 0, '', NOW()) "
                + "ON DUPLICATE KEY UPDATE nick_name=VALUES(nick_name), email=VALUES(email)",
                TEST_USER_ID, "alice@example.com", "alice", "fixture-password-not-used");
    }

    @AfterAll
    void cleanupTestUser() {
        jdbcTemplate.update("DELETE FROM user_info WHERE user_id = ?", TEST_USER_ID);
    }

    private String validToken() {
        return tokenService.encode(
                new JwtClaims((long) TEST_USER_ID, "alice", "alice@example.com", null, null, Map.of()),
                Duration.ofHours(1));
    }

    private String expiredToken() {
        return tokenService.encode(
                new JwtClaims((long) TEST_USER_ID, "alice", "alice@example.com", null, null, Map.of()),
                Duration.ofSeconds(-10));
    }

    // ---- response wrapping ----

    @Test
    void pojo_isWrapped_intoBean() throws Exception {
        mvc.perform(get("/console/_contract/pojo").header("Authorization", "GRJWT " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.msg_show").value("OK"))
                .andExpect(jsonPath("$.data.bean.id").value(42))
                .andExpect(jsonPath("$.data.bean.name").value("alice"))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list").isEmpty());
    }

    @Test
    void list_isWrapped_intoListField() throws Exception {
        mvc.perform(get("/console/_contract/list").header("Authorization", "GRJWT " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list.length()").value(2))
                .andExpect(jsonPath("$.data.list[0].name").value("a"))
                .andExpect(jsonPath("$.data.bean").isMap())
                .andExpect(jsonPath("$.data.bean").isEmpty());
    }

    @Test
    void page_isWrapped_dataListPlusBeanTotal_noPageSizeKeys() throws Exception {
        var result = mvc.perform(get("/console/_contract/page?page=2&page_size=2")
                        .header("Authorization", "GRJWT " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list.length()").value(2))
                .andExpect(jsonPath("$.data.bean.total").value(5))
                .andReturn();
        // 严格校验：响应顶层 data 不应含 page / page_size 字段
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        JsonNode data = body.path("data");
        org.junit.jupiter.api.Assertions.assertFalse(data.has("page"),
                "data should not contain top-level 'page' field; rainbond-console / kuship-ui 不依赖该字段");
        org.junit.jupiter.api.Assertions.assertFalse(data.has("page_size"),
                "data should not contain top-level 'page_size' field");
    }

    @Test
    void apiResult_isIdempotent() throws Exception {
        mvc.perform(get("/console/_contract/api-result").header("Authorization", "GRJWT " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.custom").value("yes"));
    }

    @Test
    void string_returnType_isNotWrapped_byDesign() throws Exception {
        // String 返回类型由 advice 显式排除（Spring 对 String 有特殊 cast 逻辑）
        // 业务若想让 string-like 数据走 general_message，需显式 return GeneralMessage.ok(Map.of("value", str))
        var result = mvc.perform(get("/console/_contract/string").header("Authorization", "GRJWT " + validToken()))
                .andExpect(status().isOk())
                .andReturn();
        org.junit.jupiter.api.Assertions.assertEquals("hello", result.getResponse().getContentAsString());
    }

    @Test
    void skipResponseWrapper_methodLevel() throws Exception {
        mvc.perform(get("/console/_contract/skip").header("Authorization", "GRJWT " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.raw").value(true))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void skipResponseWrapper_classLevel() throws Exception {
        mvc.perform(get("/console/_contract/skip-class/raw").header("Authorization", "GRJWT " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.class_level_skip").value(true))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    // ---- exception mapping ----

    @Test
    void serviceHandleException_passesCodeMsgMsgShow() throws Exception {
        mvc.perform(get("/console/_contract/throw-service").header("Authorization", "GRJWT " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("team not found"))
                .andExpect(jsonPath("$.msg_show").value("团队不存在"));
    }

    @Test
    void runtimeException_fallback_500_withTraceId() throws Exception {
        mvc.perform(get("/console/_contract/throw-runtime").header("Authorization", "GRJWT " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg_show").value("系统异常"))
                .andExpect(jsonPath("$.data.bean.trace_id").exists())
                .andExpect(header().exists(TraceIdFilter.HEADER_NAME));
    }

    @Test
    void validation_fails_returns400_withFieldErrors() throws Exception {
        mvc.perform(post("/console/_contract/validate")
                        .header("Authorization", "GRJWT " + validToken())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"age\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg_show").value("参数校验失败"))
                .andExpect(jsonPath("$.data.bean.errors").isArray());
    }

    @Test
    void unreadableBody_returns400() throws Exception {
        mvc.perform(post("/console/_contract/validate")
                        .header("Authorization", "GRJWT " + validToken())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg_show").value("请求体解析失败"));
    }

    @Test
    void illegalArgument_returns400() throws Exception {
        mvc.perform(get("/console/_contract/page?page=0")
                        .header("Authorization", "GRJWT " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg_show").value("参数校验失败"));
    }

    // ---- JWT ----

    @Test
    void noToken_on_protected_path_returns401() throws Exception {
        mvc.perform(get("/console/_contract/pojo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg_show").value("未认证或 token 失效"))
                .andExpect(jsonPath("$.msg").value("missing token"));
    }

    @Test
    void expiredToken_returns401_withReason() throws Exception {
        mvc.perform(get("/console/_contract/pojo").header("Authorization", "GRJWT " + expiredToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("token expired"))
                .andExpect(jsonPath("$.msg_show").value("未认证或 token 失效"));
    }

    @Test
    void malformedToken_returns401() throws Exception {
        mvc.perform(get("/console/_contract/pojo").header("Authorization", "GRJWT not-a-valid-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.msg").value("malformed token"));
    }

    @Test
    void lowercase_jwt_prefix_alsoWorks() throws Exception {
        mvc.perform(get("/console/_contract/pojo").header("Authorization", "jwt " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void healthz_remainsPermitAll() throws Exception {
        mvc.perform(get("/console/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/console/healthz/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ---- TenantContext ----

    @Test
    void tenantContext_isPopulated_fromPathVariables() throws Exception {
        mvc.perform(get("/console/_contract/teams/myteam/regions/myregion/echo")
                        .header("Authorization", "GRJWT " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.team_name").value("myteam"))
                .andExpect(jsonPath("$.data.bean.region_name").value("myregion"));
    }

    // ---- TraceId ----

    @Test
    void traceId_inResponseHeader() throws Exception {
        var first = mvc.perform(get("/console/healthz")).andReturn().getResponse().getHeader(TraceIdFilter.HEADER_NAME);
        var second = mvc.perform(get("/console/healthz")).andReturn().getResponse().getHeader(TraceIdFilter.HEADER_NAME);
        org.junit.jupiter.api.Assertions.assertNotNull(first);
        org.junit.jupiter.api.Assertions.assertNotNull(second);
        org.junit.jupiter.api.Assertions.assertNotEquals(first, second, "consecutive traceIds must differ");
    }
}
