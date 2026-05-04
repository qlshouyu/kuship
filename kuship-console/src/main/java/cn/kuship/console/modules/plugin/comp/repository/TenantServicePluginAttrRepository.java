package cn.kuship.console.modules.plugin.comp.repository;

import cn.kuship.console.modules.plugin.comp.entity.TenantServicePluginAttr;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantServicePluginAttrRepository extends JpaRepository<TenantServicePluginAttr, Integer> {

    List<TenantServicePluginAttr> findByServiceIdAndPluginId(String serviceId, String pluginId);

    void deleteByServiceIdAndPluginId(String serviceId, String pluginId);
}
