package cn.kuship.console.modules.enterprise.repository;

import cn.kuship.console.modules.enterprise.entity.ConsoleSysConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsoleSysConfigRepository extends JpaRepository<ConsoleSysConfig, Integer> {

    /** 按 key 集合查（key 全局唯一）。 */
    List<ConsoleSysConfig> findByKeyIn(List<String> keys);

    /** 按单个 key 查（key 全局唯一，对齐 rainbond ConsoleSysConfig.objects.filter(key=...).first()）。 */
    Optional<ConsoleSysConfig> findFirstByKey(String key);
}
