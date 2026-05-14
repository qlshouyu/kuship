package cn.kuship.console.modules.application.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.infrastructure.region.api.ServiceVolumeOperations;
import cn.kuship.console.infrastructure.region.exception.RegionApiException;
import cn.kuship.console.modules.application.entity.ServiceGroup;
import cn.kuship.console.modules.application.entity.ServiceGroupRelation;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.entity.TenantServiceMountRelation;
import cn.kuship.console.modules.application.entity.TenantServiceVolume;
import cn.kuship.console.modules.application.repository.ServiceGroupRelationRepository;
import cn.kuship.console.modules.application.repository.ServiceGroupRepository;
import cn.kuship.console.modules.application.repository.TenantServiceMountRelationRepository;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.application.repository.TenantServiceVolumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 组件存储挂载（mnt）业务逻辑。
 *
 * <p>Python 锚点：{@code console/services/app_config/mnt_service.py::AppMntService}
 * 主要对齐：{@code get_service_mnt_details} / {@code get_service_unmount_volume_list} /
 * {@code add_service_mnt_relation} / {@code delete_service_mnt_relation}
 */
@Service
public class AppMntService {

    private static final Logger log = LoggerFactory.getLogger(AppMntService.class);

    /** 有状态组件的 extend_method 值集合（rainbond 枚举）。 */
    private static final Set<String> STATEFUL_EXTEND_METHODS = Set.of("state", "singleton");

    /** 不可共享挂载的 volume_type 集合。 */
    private static final Set<String> UNSHARED_VOLUME_TYPES = Set.of("config-file", "local-path");

    private final TenantServiceMountRelationRepository mntRepo;
    private final TenantServiceVolumeRepository volumeRepo;
    private final TenantServiceRepository serviceRepo;
    private final ServiceGroupRelationRepository groupRelationRepo;
    private final ServiceGroupRepository groupRepo;
    private final ServiceVolumeOperations volumeOperations;

    public AppMntService(TenantServiceMountRelationRepository mntRepo,
                          TenantServiceVolumeRepository volumeRepo,
                          TenantServiceRepository serviceRepo,
                          ServiceGroupRelationRepository groupRelationRepo,
                          ServiceGroupRepository groupRepo,
                          ServiceVolumeOperations volumeOperations) {
        this.mntRepo = mntRepo;
        this.volumeRepo = volumeRepo;
        this.serviceRepo = serviceRepo;
        this.groupRelationRepo = groupRelationRepo;
        this.groupRepo = groupRepo;
        this.volumeOperations = volumeOperations;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 查询：已挂载列表
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 返回当前组件已挂载的依赖存储列表。
     *
     * <p>Python 锚点：{@code AppMntService.get_service_mnt_details}
     *
     * @param tenantId    租户 ID
     * @param serviceId   当前组件 service_id
     * @return 已挂载列表，每项含 local_vol_path / dep_vol_name 等字段
     */
    public List<Map<String, Object>> getMounted(String tenantId, String serviceId) {
        List<TenantServiceMountRelation> mnts = mntRepo.findByTenantIdAndServiceId(tenantId, serviceId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (TenantServiceMountRelation mnt : mnts) {
            // 查找被挂载的 volume
            List<TenantServiceVolume> volumes = volumeRepo.findByServiceId(mnt.getDepServiceId());
            Optional<TenantServiceVolume> depVolOpt = volumes.stream()
                    .filter(v -> mnt.getMntName().equals(v.getVolumeName()))
                    .findFirst();
            if (depVolOpt.isEmpty()) {
                continue;
            }
            TenantServiceVolume depVol = depVolOpt.get();
            // 查找被挂载组件信息
            Optional<TenantService> depServiceOpt = serviceRepo.findByServiceId(mnt.getDepServiceId());
            if (depServiceOpt.isEmpty()) {
                continue;
            }
            TenantService depService = depServiceOpt.get();
            // 查找被挂载组件所属应用
            String depAppGroup = resolveGroupName(tenantId, depService.getServiceId(), depService.getServiceRegion());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("local_vol_path", mnt.getMntDir());
            item.put("dep_vol_name", depVol.getVolumeName());
            item.put("dep_vol_path", depVol.getVolumePath());
            item.put("dep_vol_type", depVol.getVolumeType());
            item.put("dep_app_name", depService.getServiceCname());
            item.put("dep_app_group", depAppGroup);
            item.put("dep_vol_id", depVol.getId());
            item.put("dep_group_id", resolveGroupId(tenantId, depService.getServiceId(), depService.getServiceRegion()));
            item.put("dep_app_alias", depService.getServiceAlias());
            result.add(item);
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 查询：未挂载（可挂载）列表
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 返回租户内其他组件中可被当前组件挂载但尚未挂载的存储列表。
     *
     * <p>Python 锚点：{@code AppMntService.get_service_unmount_volume_list}
     * 过滤规则：
     * <ol>
     *   <li>排除当前组件自身的存储</li>
     *   <li>排除 config-file / local-path 类型（本 change 不支持 config-file 挂载）</li>
     *   <li>只保留 access_mode = RWX 的存储</li>
     *   <li>排除有状态组件（stateful）的存储（有状态组件只有 config-file 才可共享，此处不处理）</li>
     *   <li>排除已挂载的存储（已在 mnt_relation 中的）</li>
     * </ol>
     *
     * @param tenantId    租户 ID
     * @param serviceId   当前组件 service_id
     * @param regionName  region 名称（用于过滤同 region 的组件）
     * @param depAppGroup 按应用组名过滤（空字符串不过滤）
     * @param configName  按存储名或路径关键词过滤（空字符串不过滤）
     * @return 可挂载列表
     */
    public List<Map<String, Object>> getUnmounted(String tenantId, String serviceId,
                                                    String regionName, String depAppGroup, String configName) {
        // 已挂载的 volume ID 集合
        Set<Integer> mountedVolumeIds = mntRepo.findByTenantIdAndServiceId(tenantId, serviceId).stream()
                .flatMap(mnt -> volumeRepo.findByServiceId(mnt.getDepServiceId()).stream()
                        .filter(v -> mnt.getMntName().equals(v.getVolumeName()))
                        .map(TenantServiceVolume::getId))
                .collect(Collectors.toSet());

        // 同 region 下其他组件 service_id 集合
        List<TenantService> tenantServices = serviceRepo.findByTenantId(tenantId);
        Set<String> statefulServiceIds = tenantServices.stream()
                .filter(s -> isStateful(s.getExtendMethod()))
                .map(TenantService::getServiceId)
                .collect(Collectors.toSet());

        Set<String> otherServiceIds = tenantServices.stream()
                .filter(s -> !serviceId.equals(s.getServiceId()))
                .map(TenantService::getServiceId)
                .collect(Collectors.toSet());

        List<Map<String, Object>> result = new ArrayList<>();
        for (String svcId : otherServiceIds) {
            // 有状态组件的 RWX 存储不可共享（config-file 可以，但本 change 暂不支持）
            if (statefulServiceIds.contains(svcId)) {
                continue;
            }
            List<TenantServiceVolume> vols = volumeRepo.findByServiceId(svcId);
            for (TenantServiceVolume vol : vols) {
                // 过滤不可共享的类型
                if (UNSHARED_VOLUME_TYPES.contains(vol.getVolumeType())) {
                    continue;
                }
                // 只取 RWX 存储
                if (!"RWX".equalsIgnoreCase(vol.getAccessMode())) {
                    continue;
                }
                // 过滤已挂载
                if (mountedVolumeIds.contains(vol.getId())) {
                    continue;
                }
                Optional<TenantService> depSvcOpt = serviceRepo.findByServiceId(svcId);
                if (depSvcOpt.isEmpty()) continue;
                TenantService depSvc = depSvcOpt.get();
                String appGroup = resolveGroupName(tenantId, svcId, depSvc.getServiceRegion());

                // 过滤 depAppGroup
                if (!depAppGroup.isEmpty() && !depAppGroup.equals(appGroup)) {
                    continue;
                }
                // 过滤 configName（按 volume_name 或 volume_path 包含）
                if (!configName.isEmpty()
                        && !vol.getVolumeName().contains(configName)
                        && (vol.getVolumePath() == null || !vol.getVolumePath().contains(configName))) {
                    continue;
                }

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("dep_app_name", depSvc.getServiceCname());
                item.put("dep_app_group", appGroup);
                item.put("dep_vol_name", vol.getVolumeName());
                item.put("dep_vol_path", vol.getVolumePath());
                item.put("dep_vol_type", vol.getVolumeType());
                item.put("dep_vol_id", vol.getId());
                item.put("dep_group_id", resolveGroupId(tenantId, svcId, depSvc.getServiceRegion()));
                item.put("dep_app_alias", depSvc.getServiceAlias());
                result.add(item);
            }
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 写操作：批量挂载
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 批量挂载依赖存储。
     *
     * <p>Python 锚点：{@code AppMntService.batch_mnt_serivce_volume}
     * 先 region addDepVolumes，再写本地 mnt_relation；region 失败不阻止本地写入。
     *
     * @param service    当前组件实体
     * @param tenantName 租户名
     * @param tenantId   租户 ID
     * @param depVolId   被挂载的 volume ID
     * @param localPath  本地挂载路径
     */
    @Transactional
    public void addMnt(TenantService service, String tenantName, String tenantId,
                        Integer depVolId, String localPath) {
        TenantServiceVolume depVol = volumeRepo.findById(depVolId)
                .orElseThrow(() -> new ServiceHandleException(404, "volume not found", "存储卷不存在"));

        // config-file 类型暂不支持挂载（依赖 service_config_file 表，未迁移）
        if ("config-file".equals(depVol.getVolumeType())) {
            throw new ServiceHandleException(400, "config-file mount not supported",
                    "config-file volume 暂不支持挂载，请升级后使用");
        }

        // 组件创建完成时才调 region（对齐 Python create_status == "complete"）
        if ("complete".equals(service.getCreateStatus())) {
            Map<String, Object> body = Map.of(
                    "depend_service_id", depVol.getServiceId(),
                    "volume_name", depVol.getVolumeName(),
                    "volume_path", localPath.strip(),
                    "enterprise_id", tenantId,
                    "volume_type", depVol.getVolumeType() != null ? depVol.getVolumeType() : "share-file"
            );
            try {
                volumeOperations.addDepVolumes(service.getServiceRegion(), tenantName,
                        service.getServiceAlias(), body);
            } catch (RegionApiException e) {
                log.warn("[AppMnt] addDepVolumes region error, writing local anyway: {}", e.getMessage());
            }
        }

        // 写本地表（幂等：已存在则不重复写）
        Optional<TenantServiceMountRelation> existing = mntRepo.findByServiceIdAndDepServiceIdAndMntName(
                service.getServiceId(), depVol.getServiceId(), depVol.getVolumeName());
        if (existing.isEmpty()) {
            TenantServiceMountRelation rel = new TenantServiceMountRelation();
            rel.setTenantId(tenantId);
            rel.setServiceId(service.getServiceId());
            rel.setDepServiceId(depVol.getServiceId());
            rel.setMntName(depVol.getVolumeName());
            rel.setMntDir(localPath.strip());
            mntRepo.save(rel);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 写操作：取消挂载
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 取消挂载依赖存储。
     *
     * <p>Python 锚点：{@code AppMntService.delete_service_mnt_relation}
     * region 返回 404 时直接忽略，继续删本地行（与 Python 端一致）。
     *
     * @param service    当前组件实体
     * @param tenantName 租户名
     * @param tenantId   租户 ID
     * @param depVolId   被取消挂载的 volume ID
     */
    @Transactional
    public void deleteMnt(TenantService service, String tenantName, String tenantId, Integer depVolId) {
        TenantServiceVolume depVol = volumeRepo.findById(depVolId)
                .orElseThrow(() -> new ServiceHandleException(404, "volume not found", "存储卷不存在"));

        if ("complete".equals(service.getCreateStatus())) {
            Map<String, Object> body = Map.of(
                    "depend_service_id", depVol.getServiceId(),
                    "volume_name", depVol.getVolumeName(),
                    "enterprise_id", tenantName
            );
            try {
                volumeOperations.deleteDepVolumes(service.getServiceRegion(), tenantName,
                        service.getServiceAlias(), body);
            } catch (RegionApiException e) {
                if (e.getCode() == 404) {
                    log.debug("[AppMnt] depvolume not found in region, deleting local record: {}", e.getMessage());
                } else {
                    log.warn("[AppMnt] deleteDepVolumes region error, deleting local anyway: {}", e.getMessage());
                }
            }
        }

        mntRepo.deleteByServiceIdAndDepServiceIdAndMntName(
                service.getServiceId(), depVol.getServiceId(), depVol.getVolumeName());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 内部工具
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isStateful(String extendMethod) {
        if (extendMethod == null) return false;
        return STATEFUL_EXTEND_METHODS.contains(extendMethod.toLowerCase());
    }

    private String resolveGroupName(String tenantId, String serviceId, String regionName) {
        return groupRelationRepo.findByServiceId(serviceId)
                .flatMap(rel -> groupRepo.findById(rel.getGroupId()))
                .map(ServiceGroup::getGroupName)
                .orElse("未分组");
    }

    private Integer resolveGroupId(String tenantId, String serviceId, String regionName) {
        return groupRelationRepo.findByServiceId(serviceId)
                .flatMap(rel -> groupRepo.findById(rel.getGroupId()))
                .map(ServiceGroup::getId)
                .orElse(-1);
    }
}
