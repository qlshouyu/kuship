package cn.kuship.console.modules.plugin.comp.repository;

import cn.kuship.console.modules.plugin.comp.entity.ServicePluginConfigVar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServicePluginConfigVarRepository extends JpaRepository<ServicePluginConfigVar, Integer> {

    List<ServicePluginConfigVar> findByServiceIdAndPluginId(String serviceId, String pluginId);

    void deleteByServiceIdAndPluginId(String serviceId, String pluginId);
}
