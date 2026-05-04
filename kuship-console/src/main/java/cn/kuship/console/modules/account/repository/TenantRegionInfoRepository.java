package cn.kuship.console.modules.account.repository;

import cn.kuship.console.modules.account.entity.TenantRegionInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantRegionInfoRepository extends JpaRepository<TenantRegionInfo, Integer> {

    List<TenantRegionInfo> findByTenantId(String tenantId);

    Optional<TenantRegionInfo> findByTenantIdAndRegionName(String tenantId, String regionName);
}
