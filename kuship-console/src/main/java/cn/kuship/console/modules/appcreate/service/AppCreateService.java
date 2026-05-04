package cn.kuship.console.modules.appcreate.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.util.UuidGenerator;
import cn.kuship.console.infrastructure.region.api.ServiceOperations;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.appcreate.entity.ServiceSourceInfo;
import cn.kuship.console.modules.appcreate.repository.ServiceSourceInfoRepository;
import cn.kuship.console.modules.application.entity.ServiceGroup;
import cn.kuship.console.modules.application.entity.ServiceGroupRelation;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.ServiceGroupRelationRepository;
import cn.kuship.console.modules.application.repository.ServiceGroupRepository;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/** 共享创建组件 service：3 个 controller 都用。 */
@Service
public class AppCreateService {

    private static final Logger log = LoggerFactory.getLogger(AppCreateService.class);

    private final TenantServiceRepository serviceRepo;
    private final ServiceSourceInfoRepository sourceRepo;
    private final ServiceGroupRepository groupRepo;
    private final ServiceGroupRelationRepository relationRepo;
    private final TenantsRepository tenantsRepo;
    private final ServiceOperations serviceOperations;
    private final RegionServicePayloadBuilder payloadBuilder;

    public AppCreateService(TenantServiceRepository serviceRepo,
                              ServiceSourceInfoRepository sourceRepo,
                              ServiceGroupRepository groupRepo,
                              ServiceGroupRelationRepository relationRepo,
                              TenantsRepository tenantsRepo,
                              ServiceOperations serviceOperations,
                              RegionServicePayloadBuilder payloadBuilder) {
        this.serviceRepo = serviceRepo;
        this.sourceRepo = sourceRepo;
        this.groupRepo = groupRepo;
        this.relationRepo = relationRepo;
        this.tenantsRepo = tenantsRepo;
        this.serviceOperations = serviceOperations;
        this.payloadBuilder = payloadBuilder;
    }

    /**
     * 通用组件创建。事务包裹 console DB 写入 + region API 调用，region 失败自动 rollback。
     *
     * @param callRegion 是否调用 region createService（third_party 不调用）
     */
    @Transactional
    public TenantService createComponent(String teamName, Integer groupId, String regionName,
                                            ServiceConfigurer configurer, ServiceSourceConfigurer sourceConfigurer,
                                            boolean callRegion, Integer creatorUserId) {
        Tenants team = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        ServiceGroup group = null;
        if (groupId != null && groupId > 0) {
            group = groupRepo.findById(groupId)
                    .orElseThrow(() -> new ServiceHandleException(404, "application not found", "应用不存在"));
        }

        String serviceId = UuidGenerator.makeUuid();
        String serviceAlias = "gr" + serviceId.substring(0, 6);

        TenantService s = new TenantService();
        s.setServiceId(serviceId);
        s.setServiceAlias(serviceAlias);
        s.setServiceKey(serviceAlias);
        s.setTenantId(team.getTenantId());
        s.setServiceRegion(regionName);
        s.setNamespace(team.getNamespace());
        s.setCreater(creatorUserId);
        s.setCreateTime(LocalDateTime.now());
        s.setUpdateTime(LocalDateTime.now());
        s.setCreateStatus("creating");
        // 默认值
        s.setVersion("latest");
        s.setUpdateVersion(1);
        s.setMinNode(1);
        s.setMinCpu(120);
        s.setMinMemory(128);
        s.setContainerGpu(0);
        s.setTotalMemory(128);
        s.setInnerPort(0);
        s.setServicePort(0);
        s.setExtendMethod("stateless");
        s.setProtocol("");
        s.setPortType("multi_outer");
        s.setVolumeType("share-file");
        s.setIsService(false);
        s.setWebService(false);
        s.setCodeUpload(false);
        s.setOpenWebhooks(false);
        s.setUpgrate(false);
        s.setBuildUpgrade(false);
        s.setServerType("");
        s.setUpdateVersion(1);
        s.setGitProjectId(0);
        s.setTenantServiceGroupId(0);
        s.setArch("amd64");

        configurer.configure(s, serviceId, serviceAlias);

        // service_alias 唯一性预检
        serviceRepo.findByTenantIdAndServiceAlias(team.getTenantId(), s.getServiceAlias())
                .ifPresent(existing -> {
                    throw new ServiceHandleException(400, "service_alias already exists", "组件别名已存在");
                });

        TenantService saved = serviceRepo.save(s);

        ServiceSourceInfo source = new ServiceSourceInfo();
        source.setServiceId(serviceId);
        source.setTeamId(team.getTenantId());
        source.setCreateTime(LocalDateTime.now());
        if (sourceConfigurer != null) {
            sourceConfigurer.configure(source);
        }
        sourceRepo.save(source);

        if (group != null) {
            ServiceGroupRelation rel = new ServiceGroupRelation();
            rel.setServiceId(serviceId);
            rel.setGroupId(group.getId());
            rel.setTenantId(team.getTenantId());
            rel.setRegionName(regionName);
            relationRepo.save(rel);
        }

        if (callRegion) {
            try {
                Map<String, Object> body = payloadBuilder.build(saved, source);
                serviceOperations.createService(regionName, team.getTenantName(), body);
                saved.setCreateStatus("complete");
                saved.setUpdateTime(LocalDateTime.now());
                serviceRepo.save(saved);
            } catch (Exception ex) {
                log.warn("region createService failed for service_id={}: {}", serviceId, ex.getMessage());
                throw ex;
            }
        } else {
            saved.setCreateStatus("complete");
            serviceRepo.save(saved);
        }

        return saved;
    }

    @FunctionalInterface
    public interface ServiceConfigurer {
        void configure(TenantService s, String serviceId, String serviceAlias);
    }

    @FunctionalInterface
    public interface ServiceSourceConfigurer {
        void configure(ServiceSourceInfo source);
    }
}
