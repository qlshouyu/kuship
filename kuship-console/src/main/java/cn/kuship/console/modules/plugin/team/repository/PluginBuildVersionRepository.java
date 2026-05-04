package cn.kuship.console.modules.plugin.team.repository;

import cn.kuship.console.modules.plugin.team.entity.PluginBuildVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PluginBuildVersionRepository extends JpaRepository<PluginBuildVersion, Integer> {

    List<PluginBuildVersion> findByPluginIdOrderByBuildTimeDesc(String pluginId);

    Optional<PluginBuildVersion> findByPluginIdAndBuildVersion(String pluginId, String buildVersion);

    void deleteByPluginId(String pluginId);
}
