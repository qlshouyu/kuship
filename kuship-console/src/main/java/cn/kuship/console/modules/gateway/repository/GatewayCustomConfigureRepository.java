package cn.kuship.console.modules.gateway.repository;

import cn.kuship.console.modules.gateway.entity.GatewayCustomConfigure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** {@code gateway_custom_configuration} 表 Repository。 */
public interface GatewayCustomConfigureRepository extends JpaRepository<GatewayCustomConfigure, Integer> {

    Optional<GatewayCustomConfigure> findByRuleId(String ruleId);

    boolean existsByRuleId(String ruleId);
}
