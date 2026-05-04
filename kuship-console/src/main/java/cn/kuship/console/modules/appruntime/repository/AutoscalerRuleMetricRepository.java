package cn.kuship.console.modules.appruntime.repository;

import cn.kuship.console.modules.appruntime.entity.AutoscalerRuleMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AutoscalerRuleMetricRepository extends JpaRepository<AutoscalerRuleMetric, Integer> {

    List<AutoscalerRuleMetric> findByRuleId(String ruleId);

    void deleteByRuleId(String ruleId);
}
