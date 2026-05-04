package cn.kuship.console.modules.appmarket.market.repository;

import cn.kuship.console.modules.appmarket.market.entity.RainbondCenterApp;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RainbondCenterAppRepository extends JpaRepository<RainbondCenterApp, Integer> {

    Optional<RainbondCenterApp> findByAppId(String appId);

    Page<RainbondCenterApp> findByEnterpriseIdAndScope(String enterpriseId, String scope, Pageable pageable);

    @Query("SELECT a FROM RainbondCenterApp a WHERE a.enterpriseId = :eid")
    Page<RainbondCenterApp> findByEnterpriseId(@Param("eid") String enterpriseId, Pageable pageable);

    @Query("SELECT a FROM RainbondCenterApp a, CenterAppTagRelation r WHERE a.appId = r.appId "
            + "AND a.enterpriseId = :eid AND r.tagId = :tagId")
    Page<RainbondCenterApp> findByEnterpriseIdAndTagId(@Param("eid") String enterpriseId,
                                                          @Param("tagId") Integer tagId,
                                                          Pageable pageable);

    List<RainbondCenterApp> findByEnterpriseId(String enterpriseId);

    void deleteByAppId(String appId);
}
