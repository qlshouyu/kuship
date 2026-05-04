package cn.kuship.console.modules.plugin.team.repository;

import cn.kuship.console.modules.plugin.team.entity.PluginConfigItems;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PluginConfigItemsRepository extends JpaRepository<PluginConfigItems, Integer> {

    List<PluginConfigItems> findByPluginIdAndBuildVersion(String pluginId, String buildVersion);

    void deleteByPluginIdAndBuildVersion(String pluginId, String buildVersion);

    void deleteByPluginId(String pluginId);
}
