package cn.kuship.console.modules.plugin.team.repository;

import cn.kuship.console.modules.plugin.team.entity.TenantPluginShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantPluginShareRepository extends JpaRepository<TenantPluginShare, Integer> {

    Optional<TenantPluginShare> findByShareId(String shareId);

    List<TenantPluginShare> findByOriginPluginId(String originPluginId);

    List<TenantPluginShare> findByTenantId(String tenantId);
}
