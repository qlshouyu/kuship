package cn.kuship.console.modules.misc.config.repository;

import cn.kuship.console.modules.misc.config.entity.ConsoleSysConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConsoleSysConfigRepository extends JpaRepository<ConsoleSysConfig, Integer> {

    @Query("select c from ConsoleSysConfig c where c.key = :key")
    Optional<ConsoleSysConfig> findByKey(@Param("key") String key);

    @Query("select case when count(c) > 0 then true else false end from ConsoleSysConfig c where c.key = :key")
    boolean existsByKey(@Param("key") String key);
}
