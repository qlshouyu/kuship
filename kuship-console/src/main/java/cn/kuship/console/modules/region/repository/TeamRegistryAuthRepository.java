package cn.kuship.console.modules.region.repository;

import cn.kuship.console.modules.region.entity.TeamRegistryAuth;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRegistryAuthRepository extends JpaRepository<TeamRegistryAuth, Integer> {

    Optional<TeamRegistryAuth> findBySecretId(String secretId);

    List<TeamRegistryAuth> findByTenantId(String tenantId);

    List<TeamRegistryAuth> findByTenantIdAndRegionName(String tenantId, String regionName);

    /** 平台级（hub）：tenant_id='' AND region_name='' AND user_id=current。 */
    List<TeamRegistryAuth> findByTenantIdAndRegionNameAndUserId(String tenantId, String regionName, Integer userId);
}
