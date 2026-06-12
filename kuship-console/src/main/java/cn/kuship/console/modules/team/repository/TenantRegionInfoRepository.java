package cn.kuship.console.modules.team.repository;

import cn.kuship.console.modules.team.entity.TenantRegionInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantRegionInfoRepository extends JpaRepository<TenantRegionInfo, Integer> {

    List<TenantRegionInfo> findByTenantId(String tenantId);
}
