package cn.kuship.console.modules.application.repository;

import cn.kuship.console.modules.application.entity.RegionApp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegionAppRepository extends JpaRepository<RegionApp, Integer> {

    List<RegionApp> findByRegionNameAndAppIdIn(String regionName, List<Integer> appIds);

    Optional<RegionApp> findFirstByAppId(Integer appId);
}
