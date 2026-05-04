package cn.kuship.console.modules.plugin.team.repository;

import cn.kuship.console.modules.plugin.team.entity.TenantPlugin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantPluginRepository extends JpaRepository<TenantPlugin, Integer> {

    Optional<TenantPlugin> findByTenantIdAndPluginId(String tenantId, String pluginId);

    Optional<TenantPlugin> findByPluginId(String pluginId);

    List<TenantPlugin> findByTenantId(String tenantId);

    void deleteByPluginId(String pluginId);
}
