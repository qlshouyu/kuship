package cn.kuship.console.modules.team.repository;

import cn.kuship.console.modules.team.entity.TenantRegionInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantRegionInfoRepository extends JpaRepository<TenantRegionInfo, Integer> {

    List<TenantRegionInfo> findByTenantId(String tenantId);

    /** 团队在指定 region 的开通信息（取 region_tenant_name）。 */
    java.util.Optional<TenantRegionInfo> findByTenantIdAndRegionName(String tenantId, String regionName);
}
