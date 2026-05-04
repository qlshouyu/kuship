package cn.kuship.console.modules.plugin.team.repository;

import cn.kuship.console.modules.plugin.team.entity.PluginConfigGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PluginConfigGroupRepository extends JpaRepository<PluginConfigGroup, Integer> {

    List<PluginConfigGroup> findByPluginIdAndBuildVersion(String pluginId, String buildVersion);

    void deleteByPluginIdAndBuildVersion(String pluginId, String buildVersion);

    void deleteByPluginId(String pluginId);
}
