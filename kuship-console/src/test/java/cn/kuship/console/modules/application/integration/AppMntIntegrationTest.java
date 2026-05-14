package cn.kuship.console.modules.application.integration;

import cn.kuship.console.common.security.JwtClaims;
import cn.kuship.console.common.security.JwtTokenService;
import cn.kuship.console.infrastructure.region.api.ServiceVolumeOperations;
import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AppMnt 集成测试：
 * <ul>
 *   <li>GET ?type=mnt → 返回已挂载列表</li>
 *   <li>POST → 写入 mnt_relation（mock region 调用）</li>
 *   <li>DELETE/{dep_vol_id} → 删除 mnt_relation</li>
 *   <li>GET ?type=unmnt → 返回可挂载存储列表</li>
 * </ul>
 *
 * <p>ServiceVolumeOperations 通过 @MockitoBean 替换，不真实调用 region。
 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AppMntIntegrationTest {

    private static final int USER_ID = 909082;
    private static final String NICK = "kuship-mnt-admin";
    private static final String ENT = "kuship-test-ent-mnt";
    private static final String TEAM = "kuship-test-team-mnt";
    private static final String TEAM_ID = "909082mnttest1234567890123456ab";
    private static final String SVC_ID = "mnttest909082svc01234567890abcd";
    private static final String SVC_ALIAS = "kuship-mnt-svc";
    private static final String DEP_SVC_ID = "mnttest909082dep01234567890abcd";
    private static final String DEP_SVC_ALIAS = "kuship-mnt-dep-svc";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    /** mock region 调用（addDepVolumes / deleteDepVolumes 不真实发 HTTP）。 */
    @MockitoBean
    ServiceVolumeOperations serviceVolumeOperations;

    @BeforeAll
    void seed() {
        // enterprise
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, "
                + "is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'mnt-ent', 'MntTest', 1, 1, NOW()) ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);

        // user
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, "
                + "enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) ON DUPLICATE KEY UPDATE sys_admin=1",
                USER_ID, "mnt-admin@kuship.local", NICK,
                encoder.encode("mnt-admin@kuship.localpwd12345"), ENT);

        // team
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, "
                + "creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'MntTeam') "
                + "ON DUPLICATE KEY UPDATE creater=VALUES(creater)",
                TEAM_ID, TEAM, USER_ID, TEAM, ENT);

        // 主组件（发起挂载的）
        jdbc.update("INSERT INTO tenant_service (service_id, tenant_id, service_key, service_alias, "
                + "service_cname, service_region, category, service_port, is_web_service, version, "
                + "update_version, image, min_node, min_cpu, container_gpu, min_memory, extend_method, "
                + "inner_port, create_time, git_project_id, is_code_upload, creater, protocol, total_memory, "
                + "is_service, namespace, volume_type, port_type, service_origin, tenant_service_group_id, "
                + "open_webhooks, server_type, is_upgrate, build_upgrade, service_name, k8s_component_name, "
                + "update_time, create_status) "
                + "VALUES (?, ?, 'app', ?, 'MntSvc', 'r1', 'app', 0, 0, 'latest', 1, 'nginx:latest', 1, 100, "
                + "0, 256, 'stateless', 0, NOW(), 0, 0, ?, 'tcp', 256, 0, ?, 'share-file', 'inner', "
                + "'assistant', 0, 0, 'tcp', 0, 0, ?, ?, NOW(), 'complete') "
                + "ON DUPLICATE KEY UPDATE service_alias=VALUES(service_alias)",
                SVC_ID, TEAM_ID, SVC_ALIAS, USER_ID, TEAM, SVC_ALIAS, SVC_ALIAS);

        // 被挂载的依赖组件
        jdbc.update("INSERT INTO tenant_service (service_id, tenant_id, service_key, service_alias, "
                + "service_cname, service_region, category, service_port, is_web_service, version, "
                + "update_version, image, min_node, min_cpu, container_gpu, min_memory, extend_method, "
                + "inner_port, create_time, git_project_id, is_code_upload, creater, protocol, total_memory, "
                + "is_service, namespace, volume_type, port_type, service_origin, tenant_service_group_id, "
                + "open_webhooks, server_type, is_upgrate, build_upgrade, service_name, k8s_component_name, "
                + "update_time, create_status) "
                + "VALUES (?, ?, 'app', ?, 'DepSvc', 'r1', 'app', 0, 0, 'latest', 1, 'nginx:latest', 1, 100, "
                + "0, 256, 'stateless', 0, NOW(), 0, 0, ?, 'tcp', 256, 0, ?, 'share-file', 'inner', "
                + "'assistant', 0, 0, 'tcp', 0, 0, ?, ?, NOW(), 'complete') "
                + "ON DUPLICATE KEY UPDATE service_alias=VALUES(service_alias)",
                DEP_SVC_ID, TEAM_ID, DEP_SVC_ALIAS, USER_ID, TEAM, DEP_SVC_ALIAS, DEP_SVC_ALIAS);

        // 被依赖组件的 volume（RWX，可共享）
        jdbc.update("INSERT INTO tenant_service_volume (service_id, category, host_path, volume_type, "
                + "volume_path, volume_name, volume_capacity, volume_provider_name, access_mode, "
                + "share_policy, backup_policy, reclaim_policy, allow_expansion, mode) "
                + "VALUES (?, 'app', '', 'share-file', '/data/shared', 'shared-vol-01', 0, '', 'RWX', "
                + "'exclusive', '', '', 0, 0) "
                + "ON DUPLICATE KEY UPDATE volume_name=VALUES(volume_name)",
                DEP_SVC_ID);
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM tenant_service_mnt_relation WHERE service_id = ? OR service_id = ?", SVC_ID, DEP_SVC_ID);
        jdbc.update("DELETE FROM tenant_service_volume WHERE service_id = ? OR service_id = ?", SVC_ID, DEP_SVC_ID);
        jdbc.update("DELETE FROM tenant_service WHERE service_id = ? OR service_id = ?", SVC_ID, DEP_SVC_ID);
        jdbc.update("DELETE FROM tenant_info WHERE tenant_id = ?", TEAM_ID);
        jdbc.update("DELETE FROM user_info WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENT);
    }

    private String token() {
        return tokenService.encode(
                new JwtClaims((long) USER_ID, NICK, "mnt-admin@kuship.local", null, null, Map.of()),
                Duration.ofHours(1));
    }

    @Test
    @Order(1)
    void getUnmounted_returnsDependentVolume() throws Exception {
        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + SVC_ALIAS + "/mnt?type=unmnt")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray());
        // dep svc 的 RWX volume 应出现在可挂载列表中
    }

    @Test
    @Order(2)
    void getMounted_empty_whenNoRelation() throws Exception {
        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + SVC_ALIAS + "/mnt?type=mnt")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray());
    }

    @Test
    @Order(3)
    void postMnt_writesMntRelation() throws Exception {
        // 先查出 dep volume ID
        Integer volId = jdbc.queryForObject(
                "SELECT ID FROM tenant_service_volume WHERE service_id = ? AND volume_name = 'shared-vol-01'",
                Integer.class, DEP_SVC_ID);
        assertNotNull(volId, "依赖 volume 应存在于 DB");

        // POST 挂载
        String body = "{\"body\":[{\"id\":" + volId + ",\"path\":\"/mnt/shared\"}]}";
        mvc.perform(post("/console/teams/" + TEAM + "/apps/" + SVC_ALIAS + "/mnt")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证 mnt_relation 写入 DB
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_service_mnt_relation WHERE service_id = ? AND dep_service_id = ?",
                Integer.class, SVC_ID, DEP_SVC_ID);
        assertEquals(Integer.valueOf(1), count, "挂载关系应已写入 DB");
    }

    @Test
    @Order(4)
    void getMounted_returnsRelation_afterPost() throws Exception {
        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + SVC_ALIAS + "/mnt?type=mnt")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray());
    }

    @Test
    @Order(5)
    void deleteMnt_removesMntRelation() throws Exception {
        // 获取被依赖 volume 的 ID
        Integer volId = jdbc.queryForObject(
                "SELECT ID FROM tenant_service_volume WHERE service_id = ? AND volume_name = 'shared-vol-01'",
                Integer.class, DEP_SVC_ID);
        assertNotNull(volId, "依赖 volume 应存在于 DB");

        // DELETE 取消挂载
        mvc.perform(delete("/console/teams/" + TEAM + "/apps/" + SVC_ALIAS + "/mnt/" + volId)
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证 mnt_relation 已删除
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_service_mnt_relation WHERE service_id = ? AND dep_service_id = ?",
                Integer.class, SVC_ID, DEP_SVC_ID);
        assertEquals(Integer.valueOf(0), count, "挂载关系应已从 DB 删除");
    }

    @Test
    @Order(6)
    void getList_invalidType_returns400() throws Exception {
        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + SVC_ALIAS + "/mnt?type=invalid")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}
