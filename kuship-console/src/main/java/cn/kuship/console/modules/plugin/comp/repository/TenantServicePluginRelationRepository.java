package cn.kuship.console.modules.plugin.comp.repository;

import cn.kuship.console.modules.plugin.comp.entity.TenantServicePluginRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantServicePluginRelationRepository extends JpaRepository<TenantServicePluginRelation, Integer> {

    List<TenantServicePluginRelation> findByServiceId(String serviceId);

    Optional<TenantServicePluginRelation> findByServiceIdAndPluginId(String serviceId, String pluginId);

    void deleteByServiceIdAndPluginId(String serviceId, String pluginId);
}
