package cn.kuship.console.modules.region.repository;

import cn.kuship.console.modules.region.entity.RegionInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegionInfoEntityRepository extends JpaRepository<RegionInfo, Integer> {

    Optional<RegionInfo> findByRegionId(String regionId);

    Optional<RegionInfo> findByRegionName(String regionName);

    List<RegionInfo> findByEnterpriseId(String enterpriseId);

    Optional<RegionInfo> findByEnterpriseIdAndRegionName(String enterpriseId, String regionName);

    List<RegionInfo> findByEnterpriseIdAndStatus(String enterpriseId, String status);
}
