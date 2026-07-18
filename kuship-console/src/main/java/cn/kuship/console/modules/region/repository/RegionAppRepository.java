package cn.kuship.console.modules.region.repository;

import cn.kuship.console.modules.region.entity.RegionApp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RegionAppRepository extends JpaRepository<RegionApp, Integer> {

    List<RegionApp> findByRegionNameAndRegionAppIdIn(String regionName, Collection<String> regionAppIds);
}
