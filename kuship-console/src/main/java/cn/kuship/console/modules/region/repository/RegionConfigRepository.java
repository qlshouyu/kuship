package cn.kuship.console.modules.region.repository;

import cn.kuship.console.modules.region.entity.RegionConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RegionConfigRepository extends JpaRepository<RegionConfig, Integer> {

    Optional<RegionConfig> findByRegionName(String regionName);
}
