package cn.kuship.console.modules.grayrelease.repository;

import cn.kuship.console.modules.grayrelease.entity.GrayReleaseRecord;
import cn.kuship.console.modules.grayrelease.entity.GrayReleaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface GrayReleaseRecordRepository
        extends JpaRepository<GrayReleaseRecord, Integer>,
                JpaSpecificationExecutor<GrayReleaseRecord> {

    Optional<GrayReleaseRecord> findFirstByTenantIdAndAppIdAndStatus(
            String tenantId, Integer appId, GrayReleaseStatus status);

    Page<GrayReleaseRecord> findByTenantIdAndStatus(
            String tenantId, GrayReleaseStatus status, Pageable pageable);

    Page<GrayReleaseRecord> findByTenantId(String tenantId, Pageable pageable);

    List<GrayReleaseRecord> findByAppIdOrderByCreateTimeDesc(Integer appId);

    Optional<GrayReleaseRecord> findFirstByTenantIdAndOriginalServiceIdAndStatus(
            String tenantId, String originalServiceId, GrayReleaseStatus status);

    Optional<GrayReleaseRecord> findFirstByTenantIdAndGrayServiceIdAndStatus(
            String tenantId, String grayServiceId, GrayReleaseStatus status);

    Optional<GrayReleaseRecord> findFirstByTenantIdAndAppIdAndOriginalUpgradeGroupIdAndStatus(
            String tenantId, Integer appId, Integer upgradeGroupId, GrayReleaseStatus status);

    Optional<GrayReleaseRecord> findFirstByTenantIdAndAppIdAndGrayUpgradeGroupIdAndStatus(
            String tenantId, Integer appId, Integer upgradeGroupId, GrayReleaseStatus status);
}
