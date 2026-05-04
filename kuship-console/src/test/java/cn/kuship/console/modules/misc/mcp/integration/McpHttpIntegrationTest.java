package cn.kuship.console.modules.misc.mcp.integration;

import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies the synchronous MCP HTTP entry point — JSON mode for default Accept, SSE single-event
 * mode for {@code Accept: text/event-stream}, and the auth filter rejecting bad PATs.
 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McpHttpIntegrationTest {

    private static final int USER_ID = 909701;
    private static final String NICK = "mcp-admin";
    private static final String ENT = "kuship-test-ent-mcp-http";
    private static final String PAT = "test-pat-mcp-http-1234567890abcdef";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired LegacyPasswordEncoder encoder;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'mcp-ent', 'McpHttp', 1, 1, NOW()) ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) ON DUPLICATE KEY UPDATE sys_admin=1",
                USER_ID, "mcp-http@kuship.local", NICK,
                encoder.encode("mcp-http@kuship.localpwd12345"), ENT);
        jdbc.update("DELETE FROM user_access_key WHERE access_key = ?", PAT);
        jdbc.update("INSERT INTO user_access_key (note, user_id, access_key, expire_time) VALUES (?, ?, ?, ?)",
                "mcp-http", USER_ID, PAT, 99999999);
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM user_access_key WHERE access_key = ?", PAT);
        jdbc.update("DELETE FROM user_info WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENT);
    }

    @Test
    void unauthenticated_returns_401() throws Exception {
        mvc.perform(post("/console/mcp/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrong_pat_returns_401() throws Exception {
        mvc.perform(post("/console/mcp/query")
                        .header("Authorization", "Bearer wrong-pat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void ping_json_mode() throws Exception {
        mvc.perform(post("/console/mcp/query")
                        .header("Authorization", "Bearer " + PAT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.jsonrpc").value("2.0"));
    }

    @Test
    void initialize_returns_protocol_version() throws Exception {
        mvc.perform(post("/console/mcp/query")
                        .header("Authorization", "Bearer " + PAT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                                + "\"params\":{\"protocolVersion\":\"2024-11-05\",\"clientInfo\":{\"name\":\"test\",\"version\":\"0\"}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.protocolVersion").value("2024-11-05"))
                .andExpect(jsonPath("$.result.serverInfo.name").value("kuship-console"))
                .andExpect(jsonPath("$.result.capabilities.tools").exists());
    }

    @Test
    void tools_list_returns_eight_mvp_tools() throws Exception {
        mvc.perform(post("/console/mcp/query")
                        .header("Authorization", "Bearer " + PAT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools.length()").value(8))
                .andExpect(jsonPath("$.result.tools[?(@.name == 'get_current_user')]").exists())
                .andExpect(jsonPath("$.result.tools[?(@.name == 'list_apps')]").exists())
                .andExpect(jsonPath("$.result.tools[?(@.name == 'get_component_pods')]").exists());
    }

    @Test
    void tools_call_get_current_user() throws Exception {
        mvc.perform(post("/console/mcp/query")
                        .header("Authorization", "Bearer " + PAT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                                + "\"params\":{\"name\":\"get_current_user\",\"arguments\":{}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content[0].type").value("text"))
                .andExpect(jsonPath("$.result.content[0].text").value(
                        org.hamcrest.Matchers.containsString("\"user_id\":" + USER_ID)));
    }

    @Test
    void tools_call_unknown_tool_returns_method_not_found() throws Exception {
        mvc.perform(post("/console/mcp/query")
                        .header("Authorization", "Bearer " + PAT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\","
                                + "\"params\":{\"name\":\"no_such_tool\",\"arguments\":{}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32601));
    }

    @Test
    void list_apps_missing_team_name_returns_invalid_params() throws Exception {
        mvc.perform(post("/console/mcp/query")
                        .header("Authorization", "Bearer " + PAT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\","
                                + "\"params\":{\"name\":\"list_apps\",\"arguments\":{}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32602));
    }

    @Test
    void sse_single_event_mode() throws Exception {
        MvcResult mvcResult = mvc.perform(post("/console/mcp/query")
                        .header("Authorization", "Bearer " + PAT)
                        .header("Accept", "text/event-stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"ping\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:message")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"id\":7")));
    }
}
