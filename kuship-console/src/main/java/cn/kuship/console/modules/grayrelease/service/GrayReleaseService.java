package cn.kuship.console.modules.grayrelease.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.application.entity.ServiceGroup;
import cn.kuship.console.modules.application.repository.ServiceGroupRepository;
import cn.kuship.console.modules.grayrelease.entity.GrayReleaseRecord;
import cn.kuship.console.modules.grayrelease.entity.GrayReleaseStatus;
import cn.kuship.console.modules.grayrelease.entity.ServiceMappingEntry;
import cn.kuship.console.modules.grayrelease.repository.GrayReleaseRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 应用级灰度发布的状态机 + 业务编排。对应 rainbond Python {@code gray_release_service}。
 *
 * <p>职责拆分（add-gray-release Decision 1）：
 * <ul>
 *   <li>{@link GrayReleaseTemplateInstaller} 负责模板实例化（当前 stub）</li>
 *   <li>{@link ApisixRouteWeightUpdater} 负责调 rainbond-go core 改 ApisixRoute 权重</li>
 *   <li>本类仅做参数校验、状态机推进、record CRUD</li>
 * </ul>
 */
@Service
public class GrayReleaseService {

    private static final Logger log = LoggerFactory.getLogger(GrayReleaseService.class);

    private final GrayReleaseRecordRepository repo;
    private final GrayReleaseTemplateInstaller installer;
    private final ApisixRouteWeightUpdater apisixUpdater;
    private final ServiceGroupRepository serviceGroupRepo;
    private final boolean skipApisixUpdate;

    public GrayReleaseService(GrayReleaseRecordRepository repo,
                                 GrayReleaseTemplateInstaller installer,
                                 ApisixRouteWeightUpdater apisixUpdater,
                                 ServiceGroupRepository serviceGroupRepo,
                                 @Value("${kuship.gray-release.skip-apisix-update:false}") boolean skipApisixUpdate) {
        this.repo = repo;
        this.installer = installer;
        this.apisixUpdater = apisixUpdater;
        this.serviceGroupRepo = serviceGroupRepo;
        this.skipApisixUpdate = skipApisixUpdate;
    }

    @Transactional
    public GrayReleaseRecord createGrayRelease(Tenants team, String regionName, Integer appId,
                                                 CreateRequest req) {
        validateRatio(req.grayRatio());
        ServiceGroup app = serviceGroupRepo.findById(appId)
                .orElseThrow(() -> new ServiceHandleException(404, "app not found", "应用不存在"));
        if (!team.getTenantId().equals(app.getTenantId())) {
            throw new ServiceHandleException(403, "app does not belong to team", "应用不属于该团队");
        }
        repo.findFirstByTenantIdAndAppIdAndStatus(team.getTenantId(), appId, GrayReleaseStatus.ACTIVE)
                .ifPresent(existing -> {
                    throw new ServiceHandleException(409,
                            "active gray release already exists",
                            "该应用已存在进行中的灰度发布");
                });

        GrayReleaseTemplateInstaller.Result installed = installer.installGrayServiceGroup(
                team.getTenantId(), appId, req.templateId(), req.templateVersion(),
                req.marketName(), req.installFromCloud());

        if (!skipApisixUpdate) {
            try {
                apisixUpdater.update(regionName, team.getEnterpriseId(), team.getTenantName(),
                        appId, installed.originalServiceCname(), 80,
                        installed.originalServiceCname(), installed.grayServiceCname(),
                        req.grayRatio(), Map.of());
            } catch (RuntimeException e) {
                throw new ServiceHandleException(502,
                        "failed to update apisix route: " + e.getMessage(),
                        "apisix-route 权重更新失败：" + e.getMessage(), e);
            }
        }

        GrayReleaseRecord record = new GrayReleaseRecord();
        record.setTenantId(team.getTenantId());
        record.setRegionName(regionName);
        record.setAppId(appId);
        record.setAppName(app.getGroupName());
        record.setTemplateId(req.templateId() == null ? "" : req.templateId());
        record.setTemplateName(req.templateId() == null ? "" : req.templateId());
        record.setTemplateVersion(req.templateVersion() == null ? "" : req.templateVersion());
        record.setOriginalUpgradeGroupId(installed.originalUpgradeGroupId());
        record.setGrayUpgradeGroupId(installed.grayUpgradeGroupId());
        record.setOriginalServiceId(installed.originalServiceId());
        record.setOriginalServiceCname(installed.originalServiceCname());
        record.setGrayServiceId(installed.grayServiceId());
        record.setGrayServiceCname(installed.grayServiceCname());
        record.setServiceMappings(List.of(new ServiceMappingEntry(
                installed.originalServiceId(), installed.originalServiceCname(),
                installed.grayServiceId(), installed.grayServiceCname())));
        record.setDomainName(req.domainName() == null ? "" : req.domainName());
        record.setGrayRatio(req.grayRatio());
        record.setStatus(GrayReleaseStatus.ACTIVE);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        return repo.save(record);
    }

    @Transactional
    public GrayReleaseRecord updateGrayRatio(Tenants team, String regionName, Integer appId,
                                               String templateId, int newRatio) {
        validateRatio(newRatio);
        GrayReleaseRecord record = findActive(team.getTenantId(), appId, templateId);
        if (record.getStatus() != GrayReleaseStatus.ACTIVE) {
            throw new ServiceHandleException(422, "gray release not active",
                    "该灰度发布已结束，不可修改");
        }
        if (!skipApisixUpdate) {
            try {
                apisixUpdater.update(regionName, team.getEnterpriseId(), team.getTenantName(),
                        appId, record.getOriginalServiceCname(), 80,
                        record.getOriginalServiceCname(), record.getGrayServiceCname(),
                        newRatio, Map.of());
            } catch (RuntimeException e) {
                throw new ServiceHandleException(502,
                        "failed to update apisix route: " + e.getMessage(),
                        "apisix-route 权重更新失败", e);
            }
        }
        record.setGrayRatio(newRatio);
        record.setUpdateTime(LocalDateTime.now());
        return repo.save(record);
    }

    @Transactional
    public GrayReleaseRecord rollback(Tenants team, String regionName, Integer appId, String templateId) {
        Optional<GrayReleaseRecord> maybe = templateId == null
                ? repo.findFirstByTenantIdAndAppIdAndStatus(team.getTenantId(), appId, GrayReleaseStatus.ACTIVE)
                : Optional.ofNullable(findActive(team.getTenantId(), appId, templateId));
        if (maybe.isEmpty()) {
            return null;
        }
        GrayReleaseRecord record = maybe.get();
        if (record.getStatus() == GrayReleaseStatus.CANCELLED) {
            return record; // idempotent
        }
        if (!skipApisixUpdate) {
            try {
                apisixUpdater.update(regionName, team.getEnterpriseId(), team.getTenantName(),
                        appId, record.getOriginalServiceCname(), 80,
                        record.getOriginalServiceCname(), record.getGrayServiceCname(),
                        0, Map.of());
            } catch (RuntimeException e) {
                log.warn("[GrayRelease] rollback apisix update failed for record {} app {}; "
                        + "marking record CANCELLED anyway. cause={}",
                        record.getId(), appId, e.getMessage());
            }
        }
        installer.uninstallGrayServiceGroup(team.getTenantId(), appId, record.getGrayUpgradeGroupId());
        record.setStatus(GrayReleaseStatus.CANCELLED);
        record.setGrayRatio(0);
        record.setUpdateTime(LocalDateTime.now());
        return repo.save(record);
    }

    public Optional<GrayReleaseRecord> getActiveRecord(String tenantId, Integer appId) {
        return repo.findFirstByTenantIdAndAppIdAndStatus(tenantId, appId, GrayReleaseStatus.ACTIVE);
    }

    public Map<String, Object> getInfoByService(String tenantId, String serviceId) {
        Optional<GrayReleaseRecord> origMatch = repo
                .findFirstByTenantIdAndOriginalServiceIdAndStatus(tenantId, serviceId, GrayReleaseStatus.ACTIVE);
        if (origMatch.isPresent()) {
            return toInfo(origMatch.get(), "original", origMatch.get().getGrayServiceId());
        }
        Optional<GrayReleaseRecord> grayMatch = repo
                .findFirstByTenantIdAndGrayServiceIdAndStatus(tenantId, serviceId, GrayReleaseStatus.ACTIVE);
        if (grayMatch.isPresent()) {
            return toInfo(grayMatch.get(), "gray", grayMatch.get().getOriginalServiceId());
        }
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("is_gray_release", false);
        return empty;
    }

    public Map<String, Object> getInfoByUpgradeGroupId(String tenantId, Integer appId, Integer upgradeGroupId) {
        Optional<GrayReleaseRecord> origMatch = repo
                .findFirstByTenantIdAndAppIdAndOriginalUpgradeGroupIdAndStatus(
                        tenantId, appId, upgradeGroupId, GrayReleaseStatus.ACTIVE);
        if (origMatch.isPresent()) {
            return toInfo(origMatch.get(), "original", origMatch.get().getGrayServiceId());
        }
        Optional<GrayReleaseRecord> grayMatch = repo
                .findFirstByTenantIdAndAppIdAndGrayUpgradeGroupIdAndStatus(
                        tenantId, appId, upgradeGroupId, GrayReleaseStatus.ACTIVE);
        if (grayMatch.isPresent()) {
            return toInfo(grayMatch.get(), "gray", grayMatch.get().getOriginalServiceId());
        }
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("is_gray_release", false);
        return empty;
    }

    public Page<GrayReleaseRecord> listByTenant(String tenantId, GrayReleaseStatus status, Pageable pageable) {
        if (status == null) {
            return repo.findByTenantId(tenantId, pageable);
        }
        return repo.findByTenantIdAndStatus(tenantId, status, pageable);
    }

    public List<GrayReleaseRecord> listByApp(Integer appId) {
        return repo.findByAppIdOrderByCreateTimeDesc(appId);
    }

    private GrayReleaseRecord findActive(String tenantId, Integer appId, String templateId) {
        Optional<GrayReleaseRecord> active = repo
                .findFirstByTenantIdAndAppIdAndStatus(tenantId, appId, GrayReleaseStatus.ACTIVE);
        GrayReleaseRecord record = active
                .orElseThrow(() -> new ServiceHandleException(404,
                        "no active gray release for app " + appId,
                        "未找到活跃的灰度发布记录"));
        if (templateId != null && !templateId.equals(record.getTemplateId())) {
            throw new ServiceHandleException(404,
                    "no active gray release matching template " + templateId,
                    "未找到匹配模板的活跃灰度记录");
        }
        return record;
    }

    private Map<String, Object> toInfo(GrayReleaseRecord r, String type, String pairedServiceId) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("is_gray_release", true);
        info.put("gray_release_type", type);
        info.put("paired_service_id", pairedServiceId);
        info.put("gray_ratio", r.getGrayRatio());
        info.put("record_id", r.getId());
        info.put("status", r.getStatus().value());
        return info;
    }

    private static void validateRatio(Integer ratio) {
        if (ratio == null || ratio < 0 || ratio > 100) {
            throw new ServiceHandleException(400,
                    "gray_ratio must be between 0 and 100",
                    "灰度比例必须在0-100之间");
        }
    }

    public record CreateRequest(
            String templateId,
            String templateVersion,
            String domainName,
            int grayRatio,
            String marketName,
            boolean installFromCloud) {
    }
}
