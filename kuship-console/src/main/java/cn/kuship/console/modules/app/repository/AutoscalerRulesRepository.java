package cn.kuship.console.modules.app.repository;

import cn.kuship.console.modules.app.entity.AutoscalerRules;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AutoscalerRulesRepository extends JpaRepository<AutoscalerRules, Integer> {
    List<AutoscalerRules> findByServiceId(String serviceId);
}
