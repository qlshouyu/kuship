package cn.kuship.console.modules.enterprise.repository;

import cn.kuship.console.modules.enterprise.entity.EnterpriseUserPerm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EnterpriseUserPermRepository extends JpaRepository<EnterpriseUserPerm, Integer> {

    Optional<EnterpriseUserPerm> findFirstByEnterpriseIdAndUserId(String enterpriseId, Integer userId);

    /** 企业管理员判定：存在 identity=admin 或 is_initial_enterprise_admin 的记录。 */
    boolean existsByEnterpriseIdAndUserIdAndIdentity(String enterpriseId, Integer userId, String identity);

    Optional<EnterpriseUserPerm> findFirstByEnterpriseIdAndIsInitialEnterpriseAdminTrue(String enterpriseId);
}
