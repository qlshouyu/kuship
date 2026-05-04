package cn.kuship.console.modules.account.repository;

import cn.kuship.console.modules.account.entity.EnterpriseUserPerm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnterpriseUserPermRepository extends JpaRepository<EnterpriseUserPerm, Integer> {

    Optional<EnterpriseUserPerm> findByUserIdAndEnterpriseId(Integer userId, String enterpriseId);

    List<EnterpriseUserPerm> findByEnterpriseIdAndIdentity(String enterpriseId, String identity);

    List<EnterpriseUserPerm> findByUserId(Integer userId);
}
