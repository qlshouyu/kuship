package cn.kuship.console.modules.enterprise.service;

import cn.kuship.console.modules.enterprise.entity.ConsoleSysConfig;
import cn.kuship.console.modules.enterprise.entity.TenantEnterprise;
import cn.kuship.console.modules.enterprise.repository.ConsoleSysConfigRepository;
import cn.kuship.console.modules.enterprise.repository.TenantEnterpriseRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 企业信息：to_dict(空格 create_time)、config json/string 解析、enable_team_resource_view、特殊字段。 */
class EnterpriseInfoServiceTest {

    private final TenantEnterpriseRepository entRepo = mock(TenantEnterpriseRepository.class);
    private final ConsoleSysConfigRepository cfgRepo = mock(ConsoleSysConfigRepository.class);
    private final EnterpriseInfoService service = new EnterpriseInfoService(entRepo, cfgRepo);

    private static ConsoleSysConfig cfg(String key, String type, String value, boolean enable) {
        ConsoleSysConfig c = new ConsoleSysConfig();
        c.setKey(key);
        c.setType(type);
        c.setValue(value);
        c.setEnable(enable);
        return c;
    }

    @Test
    @SuppressWarnings("unchecked")
    void info_assembles_fields_and_parses_config() {
        TenantEnterprise e = new TenantEnterprise();
        e.setId(1);
        e.setEnterpriseId("e1");
        e.setEnterpriseName("qo8wzjsp");
        e.setEnterpriseAlias("KuShip");
        e.setCreateTime(LocalDateTime.of(2026, 6, 12, 21, 9, 0));
        e.setEnterpriseToken("");
        e.setIsActive(0);
        e.setEnableTeamResourceView(true);
        when(entRepo.findByEnterpriseId("e1")).thenReturn(Optional.of(e));
        when(cfgRepo.findByKeyIn(anyList())).thenReturn(List.of(
                cfg("OAUTH_SERVICES", "json", "[]", true),
                cfg("APPSTORE_IMAGE_HUB", "json", "{'hub_user': None, 'hub_url': None}", false),
                cfg("TITLE", "string", "", true),
                cfg("LOGO", "string", null, true)));

        Map<String, Object> b = service.info("e1");
        // to_dict
        assertThat(b).containsEntry("ID", 1).containsEntry("enterprise_alias", "KuShip")
                .containsEntry("create_time", "2026-06-12 21:09:00") // 空格格式
                .containsEntry("is_active", 0).containsEntry("enable_team_resource_view", true)
                .containsEntry("enterprise_token", "");
        assertThat((Map<String, Object>) b.get("default_region")).isEmpty();
        // config json/string
        assertThat((Map<String, Object>) b.get("oauth_services"))
                .containsEntry("enable", true).containsEntry("value", List.of());
        Map<String, Object> hub = (Map<String, Object>) b.get("appstore_image_hub");
        assertThat(hub).containsEntry("enable", false);
        assertThat((Map<String, Object>) hub.get("value")).containsEntry("hub_user", null).containsEntry("hub_url", null);
        assertThat((Map<String, Object>) b.get("title")).containsEntry("value", ""); // string ""
        assertThat((Map<String, Object>) b.get("logo")).containsEntry("value", null); // string NULL→null
        // 特殊字段
        assertThat(b).containsEntry("default_market_url", "").containsEntry("disable_logo", false);
    }
}
