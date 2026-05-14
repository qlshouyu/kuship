package cn.kuship.console.modules.appmarket.share.integration;

import cn.kuship.console.common.security.JwtClaims;
import cn.kuship.console.common.security.JwtTokenService;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.infrastructure.region.exception.RegionApiFrequentException;
import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
import cn.kuship.console.modules.appmarket.share.api.ShareOperations;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 应用 / 插件分享 6-step 状态机端到端集成测试 + 2 个独立 endpoint。
 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=integration-test-secret-key-must-be-at-least-256-bits-long-okayy"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShareLifecycleIntegrationTest {

    private static final int USER_ID = 909903;
    private static final String NICK = "kuship-share-admin";
    private static final String EMAIL = "share-admin@kuship.local";
    private static final String ENT = "kuship-test-ent-share";
    private static final String TEAM = "kuship-share-team";
    private static final String TEAM_ID = "9099030303030303share7890123xx";
    private static final String NAMESPACE = "ns-share-team";
    private static final String REGION = "rainbond";
    private static final String ALIAS = "svc1";
    private static final String SERVICE_ID = "9099030303share0001id000000abcd";
    private static final String GROUP_SHARE_ID_PREFIX = "kbsharegrp";
    private static final int GROUP_ID_INT = 909903;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokenService;
    @Autowired LegacyPasswordEncoder encoder;

    @MockitoBean ShareOperations shareOps;

    @BeforeAll
    void seed() {
        jdbc.update("INSERT INTO tenant_enterprise (enterprise_id, enterprise_name, enterprise_alias, is_active, enable_team_resource_view, create_time) "
                + "VALUES (?, 'share-ent', 'ShareTest', 1, 1, NOW()) "
                + "ON DUPLICATE KEY UPDATE enterprise_name=VALUES(enterprise_name)", ENT);
        jdbc.update("INSERT INTO user_info (user_id, email, nick_name, password, is_active, sys_admin, enterprise_id, create_time) "
                + "VALUES (?, ?, ?, ?, 1, 1, ?, NOW()) "
                + "ON DUPLICATE KEY UPDATE sys_admin=1, enterprise_id=VALUES(enterprise_id)",
                USER_ID, EMAIL, NICK, encoder.encode(EMAIL + "pwd12345"), ENT);
        jdbc.update("INSERT INTO tenant_info (tenant_id, tenant_name, is_active, create_time, update_time, creater, limit_memory, namespace, enterprise_id, tenant_alias) "
                + "VALUES (?, ?, 1, NOW(), NOW(), ?, 1024, ?, ?, 'ShareTeam') "
                + "ON DUPLICATE KEY UPDATE namespace=VALUES(namespace)",
                TEAM_ID, TEAM, USER_ID, NAMESPACE, ENT);

        jdbc.update("INSERT INTO tenant_service (service_id, tenant_id, service_key, service_alias, service_cname, service_region, "
                + "category, service_port, is_web_service, version, update_version, image, min_node, min_cpu, container_gpu, "
                + "min_memory, extend_method, inner_port, create_time, git_project_id, is_code_upload, creater, protocol, "
                + "total_memory, is_service, namespace, volume_type, port_type, service_origin, service_source, create_status, "
                + "tenant_service_group_id, open_webhooks, server_type, is_upgrate, build_upgrade, service_name, k8s_component_name, update_time, secret) "
                + "VALUES (?, ?, 'app', ?, 'svc', ?, 'app', 0, 0, 'latest', 1, 'nginx:latest', 1, 100, 0, 256, "
                + "'stateless', 0, NOW(), 0, 0, ?, 'tcp', 256, 0, ?, 'share-file', 'inner', 'assistant', 'docker_run', 'complete', "
                + "0, 1, 'tcp', 0, 0, ?, ?, NOW(), 'sec') "
                + "ON DUPLICATE KEY UPDATE service_source=VALUES(service_source)",
                SERVICE_ID, TEAM_ID, ALIAS, REGION, USER_ID, TEAM,
                "kuship-" + ALIAS, "kuship-" + ALIAS);
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM service_share_record_event WHERE record_id IN (SELECT ID FROM service_share_record WHERE team_name = ?)", TEAM);
        jdbc.update("DELETE FROM service_share_record WHERE team_name = ?", TEAM);
        jdbc.update("DELETE FROM tenant_service WHERE service_id = ?", SERVICE_ID);
        jdbc.update("DELETE FROM tenant_info WHERE tenant_id = ?", TEAM_ID);
        jdbc.update("DELETE FROM user_info WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM tenant_enterprise WHERE enterprise_id = ?", ENT);
    }

    private String token() {
        return tokenService.encode(
                new JwtClaims((long) USER_ID, NICK, EMAIL, null, null, Map.of()),
                Duration.ofHours(1));
    }

    private String createRecord() throws Exception {
        String resp = mvc.perform(post("/console/teams/" + TEAM + "/groups/" + GROUP_ID_INT + "/share/record")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"share_version\":\"1.0\",\"share_app_version_info\":\"info\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return resp.replaceAll(".*\"group_share_id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void serviceShareEvent_addsEventAndPersistsRegionShareId() throws Exception {
        String shareId = createRecord();
        when(shareOps.shareService(eq(REGION), eq(TEAM), eq(ALIAS), any())).thenReturn(Map.of(
                "share_id", "rsid-001", "event_id", "evt-001", "image_name", "img:1.0"));

        mvc.perform(post("/console/teams/" + TEAM + "/share/" + shareId + "/events/evt-001")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"service_alias\":\"" + ALIAS + "\",\"service_key\":\"key1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.region_share_id").value("rsid-001"))
                .andExpect(jsonPath("$.data.bean.event_status").value("start"));

        verify(shareOps).shareService(eq(REGION), eq(TEAM), eq(ALIAS), any());

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_share_record_event WHERE event_id=?",
                Integer.class, "evt-001");
        assert count != null && count == 1 : "expect 1 event row, got " + count;
    }

    @Test
    void serviceShareEvent_region5xx_rollsBackTransaction() throws Exception {
        String shareId = createRecord();
        when(shareOps.shareService(eq(REGION), eq(TEAM), eq(ALIAS), any())).thenThrow(
                new RegionApiException("share",
                        "/v2/tenants/" + NAMESPACE + "/services/" + ALIAS + "/share", "POST",
                        503, 503, "region down", "数据中心分享错误", Map.of(), null));

        mvc.perform(post("/console/teams/" + TEAM + "/share/" + shareId + "/events/evt-rollback")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"service_alias\":\"" + ALIAS + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.msg_show").value("数据中心分享错误"));

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_share_record_event WHERE event_id=?",
                Integer.class, "evt-rollback");
        assert count != null && count == 0 : "expected rollback (no event row), got " + count;
    }

    @Test
    void eventStatus_pollingUpdatesLocalEventStatus() throws Exception {
        String shareId = createRecord();
        when(shareOps.shareService(eq(REGION), eq(TEAM), eq(ALIAS), any())).thenReturn(Map.of(
                "share_id", "rsid-poll", "event_id", "evt-poll"));
        mvc.perform(post("/console/teams/" + TEAM + "/share/" + shareId + "/events/evt-poll")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"service_alias\":\"" + ALIAS + "\"}"))
                .andExpect(status().isOk());

        when(shareOps.getShareServiceResult(eq(REGION), eq(TEAM), eq(ALIAS), eq("rsid-poll"))).thenReturn(
                Map.of("status", "success"));
        mvc.perform(get("/console/teams/" + TEAM + "/share/" + shareId + "/events/evt-poll/status")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.status").value("success"));

        String localStatus = jdbc.queryForObject(
                "SELECT event_status FROM service_share_record_event WHERE event_id=?",
                String.class, "evt-poll");
        assert "success".equals(localStatus) : "expect success, got " + localStatus;
    }

    @Test
    void completeRejectedWhenEventFailed() throws Exception {
        String shareId = createRecord();
        when(shareOps.shareService(eq(REGION), eq(TEAM), eq(ALIAS), any())).thenReturn(Map.of(
                "share_id", "rsid-fail", "event_id", "evt-fail"));
        mvc.perform(post("/console/teams/" + TEAM + "/share/" + shareId + "/events/evt-fail")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"service_alias\":\"" + ALIAS + "\"}"))
                .andExpect(status().isOk());
        jdbc.update("UPDATE service_share_record_event SET event_status='failure' WHERE event_id=?", "evt-fail");

        mvc.perform(post("/console/teams/" + TEAM + "/share/" + shareId + "/complete")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.msg_show").value("存在失败事件，请放弃后重试"));
    }

    @Test
    void completeHappyWhenAllSuccess() throws Exception {
        String shareId = createRecord();
        when(shareOps.shareService(eq(REGION), eq(TEAM), eq(ALIAS), any())).thenReturn(Map.of(
                "share_id", "rsid-ok", "event_id", "evt-ok"));
        mvc.perform(post("/console/teams/" + TEAM + "/share/" + shareId + "/events/evt-ok")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"service_alias\":\"" + ALIAS + "\"}"))
                .andExpect(status().isOk());
        jdbc.update("UPDATE service_share_record_event SET event_status='success' WHERE event_id=?", "evt-ok");

        mvc.perform(post("/console/teams/" + TEAM + "/share/" + shareId + "/complete")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.status").value(1))
                .andExpect(jsonPath("$.data.bean.step").value(5))
                .andExpect(jsonPath("$.data.bean.is_success").value(true));
    }

    @Test
    void region429_propagatesMsgShow() throws Exception {
        String shareId = createRecord();
        when(shareOps.shareService(eq(REGION), eq(TEAM), eq(ALIAS), any())).thenThrow(
                new RegionApiFrequentException("share",
                        "/v2/tenants/" + NAMESPACE + "/services/" + ALIAS + "/share", "POST",
                        409, "rate limited", Map.of()));

        mvc.perform(post("/console/teams/" + TEAM + "/share/" + shareId + "/events/evt-rate")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"service_alias\":\"" + ALIAS + "\"}"))
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.msg_show").value("操作过于频繁，请稍后再试"));
    }

    @Test
    void publishStatusQuery_happy() throws Exception {
        when(shareOps.getServicePublishStatus(eq(REGION), eq(TEAM), eq("svckey"), eq("1.0")))
                .thenReturn(Map.of("status", "published"));

        mvc.perform(get("/console/teams/" + TEAM + "/apps/" + ALIAS + "/publish/status?service_key=svckey&app_version=1.0")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bean.status").value("published"));
    }

    @Test
    void appReleases_returnsEmptyWhenRegionAppIdMissing() throws Exception {
        mvc.perform(get("/console/teams/" + TEAM + "/groups/" + GROUP_ID_INT + "/releases")
                        .header("Authorization", "GRJWT " + token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg_show").value("应用不存在"));

        verify(shareOps, never()).listAppReleases(anyString(), anyString(), anyString());
    }

    @Test
    void teamNotFound_serviceShareEvent_returns404() throws Exception {
        String shareId = createRecord();
        mvc.perform(post("/console/teams/no-such-team/share/" + shareId + "/events/evt-x")
                        .header("Authorization", "GRJWT " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"service_alias\":\"" + ALIAS + "\"}"))
                .andExpect(status().isNotFound());
    }
}
