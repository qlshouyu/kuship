package cn.kuship.console.modules.app.repository;

import cn.kuship.console.modules.app.entity.AutoscalerRuleMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface AutoscalerRuleMetricsRepository extends JpaRepository<AutoscalerRuleMetrics, Integer> {
    List<AutoscalerRuleMetrics> findByRuleIdIn(Collection<String> ruleIds);
}
