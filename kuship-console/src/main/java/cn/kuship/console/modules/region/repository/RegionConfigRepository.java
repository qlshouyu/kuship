package cn.kuship.console.modules.region.repository;

import cn.kuship.console.modules.region.entity.RegionConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegionConfigRepository extends JpaRepository<RegionConfig, Integer> {

    Optional<RegionConfig> findByRegionName(String regionName);

    /** 按 region_id(uuid) 查（region 端点路径用 region_id，调用用 region_name）。 */
    Optional<RegionConfig> findByRegionId(String regionId);

    /** 企业集群列表（按 id 升序）。 */
    List<RegionConfig> findByEnterpriseIdOrderById(String enterpriseId);

    /** 企业集群列表（按状态过滤，按 id 升序）。 */
    List<RegionConfig> findByEnterpriseIdAndStatusOrderById(String enterpriseId, String status);
}
