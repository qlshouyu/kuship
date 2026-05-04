package cn.kuship.console.modules.plugin.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.plugin.team.entity.TenantPlugin;
import cn.kuship.console.modules.plugin.team.repository.TenantPluginRepository;
import org.springframework.stereotype.Component;

/** 共享的"按 team_name + plugin_id 取 Tenant + TenantPlugin"辅助。 */
@Component
public class PluginContextLoader {

    private final TenantsRepository tenantsRepo;
    private final TenantPluginRepository pluginRepo;

    public PluginContextLoader(TenantsRepository tenantsRepo, TenantPluginRepository pluginRepo) {
        this.tenantsRepo = tenantsRepo;
        this.pluginRepo = pluginRepo;
    }

    public Tenants requireTeam(String teamName) {
        return tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
    }

    public TenantPlugin requirePlugin(String teamName, String pluginId) {
        Tenants team = requireTeam(teamName);
        return pluginRepo.findByTenantIdAndPluginId(team.getTenantId(), pluginId)
                .orElseThrow(() -> new ServiceHandleException(404, "plugin not found", "插件不存在"));
    }
}
