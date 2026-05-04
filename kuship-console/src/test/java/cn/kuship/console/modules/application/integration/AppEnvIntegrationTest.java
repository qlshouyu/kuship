package cn.kuship.console.modules.application.integration;

import cn.kuship.console.common.security.JwtClaims;
import cn.kuship.console.common.security.JwtTokenService;
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

import java.time.Duration;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** AppEnv 集成测试：CRUD + 唯一性 400。env 仅本地写不调 region，可在本机直跑。 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppEnvIntegrationTest {

    private static final int USER_ID = 909071;
    private static final String NICK = "kuship-env-admin";
    private static final String ENT = "kuship-test-ent-env";
    private static final String TEAM = "kuship-test-team-env";
    private static final String TEAM_ID = "9090717171717171env1234567890123";
    private static final String SERVICE_ID = "envtest909071svc1234567890abcdef";
    private static final String SERVICE_ALIAS = "kuship-env-svc";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'env-ent', 'EnvTest', 1, 1, NOW()) ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) ON DUPLICATE KEY UPDATE sys_admin=1",
                USER_ID, "env-admin@kuship.local", NICK,
                encoder.encode("env-admin@kuship.localpwd12345"), ENT);
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'EnvTeam') "
                + "ON DUPLICATE KEY UPDATE creater=VALUES(creater)",
                TEAM_ID, TEAM, USER_ID, TEAM, ENT);
        // 插入一个 fixture 组件（tenant_service 表大量 NOT NULL 字段需要默认值）
        jdbc.update("INSERT INTO tenant_service (service_id, tenant_id, service_key, service_alias, service_cname, service_region, "
                + "category, service_port, is_web_service, version, update_version, image, min_node, min_cpu, "
                + "container_gpu, min_memory, extend_method, inner_port, create_time, git_project_id, is_code_upload, "
                + "creater, protocol, total_memory, is_service, namespace, volume_type, port_type, service_origin, "
                + "tenant_service_group_id, open_webhooks, server_type, is_upgrate, build_upgrade, service_name, "
                + "k8s_component_name, update_time) "
                + "VALUES (?, ?, 'app', ?, 'EnvSvc', 'r1', 'app', 0, 0, 'latest', 1, 'nginx:latest', 1, 100, 0, 256, "
                + "'stateless', 0, NOW(), 0, 0, ?, 'tcp', 256, 0, ?, 'share-file', 'inner', 'assistant', 0, 0, 'tcp', 0, 0, "
                + "'kuship-env-svc', 'kuship-env-svc', NOW()) "
                + "ON DUPLICATE KEY UPDATE service_alias=VALUES(service_alias)",
                SERVICE_ID, TEAM_ID, SERVICE_ALIAS, USER_ID, TEAM);
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM tenant_service_env_var WHERE service_id = ?", SERVICE_ID);
        jdbc.update("DELETE FROM tenant_service WHERE service_id = ?", SERVICE_ID);
        jdbc.update("DELETE FROM tenant_info WHERE tenant_id = ?", TEAM_ID);
        jdbc.update("DELETE FROM user_info WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENT);
    }

    private String token() {
        return tokenService.encode(
                new JwtClaims((long) USER_ID, NICK, "env-admin@kuship.local", null, null, Map.of()),
                Duration.ofHours(1));
    }

    @Test
    void env_crud_with_uniqueness() throws Exception {
        // 1. POST 新增
        mvc.perform(post("/console/teams/" + TEAM + "/apps/" + SERVICE_ALIAS + "/envs")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"DB Host\",\"attr_name\":\"DB_HOST\","
                                + "\"attr_value\":\"172.20.0.10\",\"is_change\":true,\"scope\":\"inner\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.bean.attr_name").value("DB_HOST"));

        // 2. 唯一性 400（同 attr_name + scope）
        mvc.perform(post("/console/teams/" + TEAM + "/apps/" + SERVICE_ALIAS + "/envs")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attr_name\":\"DB_HOST\",\"attr_value\":\"x\",\"scope\":\"inner\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg_show").value("环境变量名已存在"));

        // 3. GET 列表
        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + SERVICE_ALIAS + "/envs?scope=inner")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list.length()").value(1))
                .andExpect(jsonPath("$.data.list[0].attr_value").value("172.20.0.10"));
    }
}
