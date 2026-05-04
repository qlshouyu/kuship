package cn.kuship.console.modules.appmarket.market.repository;

import cn.kuship.console.modules.appmarket.market.entity.CenterAppTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CenterAppTagRepository extends JpaRepository<CenterAppTag, Integer> {

    List<CenterAppTag> findByEnterpriseIdAndIsDeletedFalse(String enterpriseId);

    Optional<CenterAppTag> findByEnterpriseIdAndName(String enterpriseId, String name);
}
