package cn.kuship.console.modules.appmarket.market.repository;

import cn.kuship.console.modules.appmarket.market.entity.CenterAppTagRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CenterAppTagRelationRepository extends JpaRepository<CenterAppTagRelation, Integer> {

    List<CenterAppTagRelation> findByAppId(String appId);

    List<CenterAppTagRelation> findByEnterpriseIdAndAppId(String enterpriseId, String appId);

    @Modifying
    @Query("DELETE FROM CenterAppTagRelation r WHERE r.appId = :appId AND r.tagId = :tagId")
    int deleteByAppIdAndTagId(@Param("appId") String appId, @Param("tagId") Integer tagId);

    void deleteByAppId(String appId);
}
