package cn.kuship.console.modules.plugin.market.repository;

import cn.kuship.console.modules.plugin.market.entity.RainbondCenterPlugin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RainbondCenterPluginRepository extends JpaRepository<RainbondCenterPlugin, Integer> {

    List<RainbondCenterPlugin> findByEnterpriseId(String enterpriseId);

    Optional<RainbondCenterPlugin> findByPluginKey(String pluginKey);
}
