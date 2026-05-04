package cn.kuship.console.modules.appruntime.repository;

import cn.kuship.console.modules.appruntime.entity.AutoscalerRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AutoscalerRuleRepository extends JpaRepository<AutoscalerRule, Integer> {

    List<AutoscalerRule> findByServiceId(String serviceId);

    Optional<AutoscalerRule> findByRuleId(String ruleId);

    void deleteByRuleId(String ruleId);
}
