package cn.kuship.console.modules.appcreate.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.infrastructure.region.api.ServiceOperations;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.appcreate.entity.TenantServiceInfoDelete;
import cn.kuship.console.modules.appcreate.repository.ServiceSourceInfoRepository;
import cn.kuship.console.modules.appcreate.repository.TenantServiceInfoDeleteRepository;
import cn.kuship.console.modules.application.entity.ServiceGroupRelation;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.ServiceGroupRelationRepository;
import cn.kuship.console.modules.application.repository.ServiceProbeRepository;
import cn.kuship.console.modules.application.repository.TenantServiceEnvVarRepository;
import cn.kuship.console.modules.application.repository.TenantServiceRelationRepository;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.application.repository.TenantServiceVolumeRepository;
import cn.kuship.console.modules.application.repository.TenantServicesPortRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/** 软删除归档 + 本地清理。 */
@Service
public class AppDeleteService {

    private final TenantServiceRepository serviceRepo;
    private final TenantServiceInfoDeleteRepository deleteRepo;
    private final ServiceSourceInfoRepository sourceRepo;
    private final ServiceGroupRelationRepository relationRepo;
    private final TenantServiceEnvVarRepository envRepo;
    private final TenantServicesPortRepository portRepo;
    private final TenantServiceVolumeRepository volumeRepo;
    private final TenantServiceRelationRepository depRepo;
    private final ServiceProbeRepository probeRepo;
    private final TenantsRepository tenantsRepo;
    private final ServiceOperations serviceOperations;

    public AppDeleteService(TenantServiceRepository serviceRepo,
                              TenantServiceInfoDeleteRepository deleteRepo,
                              ServiceSourceInfoRepository sourceRepo,
                              ServiceGroupRelationRepository relationRepo,
                              TenantServiceEnvVarRepository envRepo,
                              TenantServicesPortRepository portRepo,
                              TenantServiceVolumeRepository volumeRepo,
                              TenantServiceRelationRepository depRepo,
                              ServiceProbeRepository probeRepo,
                              TenantsRepository tenantsRepo,
                              ServiceOperations serviceOperations) {
        this.serviceRepo = serviceRepo;
        this.deleteRepo = deleteRepo;
        this.sourceRepo = sourceRepo;
        this.relationRepo = relationRepo;
        this.envRepo = envRepo;
        this.portRepo = portRepo;
        this.volumeRepo = volumeRepo;
        this.depRepo = depRepo;
        this.probeRepo = probeRepo;
        this.tenantsRepo = tenantsRepo;
        this.serviceOperations = serviceOperations;
    }

    @Transactional
    public void delete(String teamName, String serviceAlias, Integer userId) {
        Tenants team = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        TenantService s = serviceRepo.findByTenantIdAndServiceAlias(team.getTenantId(), serviceAlias)
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));

        // 1. 调 region 释放 K8s 资源（third_party 跳过）
        if (!"third_party".equals(s.getServiceSource())) {
            serviceOperations.deleteService(s.getServiceRegion(), team.getTenantName(),
                    serviceAlias, team.getEnterpriseId(), java.util.Map.of());
        }

        // 2. 归档到 tenant_service_delete
        TenantServiceInfoDelete arch = new TenantServiceInfoDelete();
        arch.setServiceId(s.getServiceId());
        arch.setTenantId(s.getTenantId());
        arch.setServiceKey(s.getServiceKey() != null ? s.getServiceKey() : "");
        arch.setServiceAlias(s.getServiceAlias());
        arch.setServiceCname(s.getServiceCname() != null ? s.getServiceCname() : "");
        arch.setServiceRegion(s.getServiceRegion() != null ? s.getServiceRegion() : "");
        arch.setDescription(s.getDescription());
        arch.setCategory(s.getCategory() != null ? s.getCategory() : "application");
        arch.setServicePort(s.getServicePort() != null ? s.getServicePort() : 0);
        arch.setWebService(s.getWebService() != null && s.getWebService());
        arch.setVersion(s.getVersion() != null ? s.getVersion() : "");
        arch.setUpdateVersion(s.getUpdateVersion() != null ? s.getUpdateVersion() : 1);
        arch.setImage(s.getImage() != null ? s.getImage() : "");
        arch.setCmd(s.getCmd());
        arch.setExtendMethod(s.getExtendMethod() != null ? s.getExtendMethod() : "stateless");
        arch.setMinNode(s.getMinNode() != null ? s.getMinNode() : 1);
        arch.setMinCpu(s.getMinCpu() != null ? s.getMinCpu() : 0);
        arch.setMinMemory(s.getMinMemory() != null ? s.getMinMemory() : 0);
        arch.setContainerGpu(s.getContainerGpu() != null ? s.getContainerGpu() : 0);
        arch.setInnerPort(s.getInnerPort() != null ? s.getInnerPort() : 0);
        arch.setHostPath(s.getHostPath());
        arch.setDeployVersion(s.getDeployVersion());
        arch.setCodeFrom(s.getCodeFrom());
        arch.setGitUrl(s.getGitUrl());
        arch.setCreateTime(s.getCreateTime());
        arch.setGitProjectId(s.getGitProjectId() != null ? s.getGitProjectId() : 0);
        arch.setCodeUpload(s.getCodeUpload() != null && s.getCodeUpload());
        arch.setCodeVersion(s.getCodeVersion());
        arch.setServiceType(s.getServiceType());
        arch.setDeleteTime(LocalDateTime.now());
        arch.setCreater(s.getCreater() != null ? s.getCreater() : userId);
        arch.setLanguage(s.getLanguage());
        arch.setProtocol(s.getProtocol() != null ? s.getProtocol() : "");
        arch.setTotalMemory(s.getTotalMemory() != null ? s.getTotalMemory() : 0);
        arch.setIsService(s.getIsService() != null && s.getIsService());
        arch.setNamespace(s.getNamespace() != null ? s.getNamespace() : "");
        arch.setVolumeType(s.getVolumeType() != null ? s.getVolumeType() : "share-file");
        arch.setPortType(s.getPortType() != null ? s.getPortType() : "multi_outer");
        arch.setServiceOrigin(s.getServiceOrigin() != null ? s.getServiceOrigin() : "assistant");
        arch.setServiceSource(s.getServiceSource());
        arch.setCreateStatus(s.getCreateStatus());
        arch.setUpdateTime(LocalDateTime.now());
        arch.setTenantServiceGroupId(s.getTenantServiceGroupId() != null ? s.getTenantServiceGroupId() : 0);
        arch.setOpenWebhooks(s.getOpenWebhooks() != null && s.getOpenWebhooks());
        arch.setCheckUuid(s.getCheckUuid());
        arch.setCheckEventId(s.getCheckEventId());
        arch.setDockerCmd(s.getDockerCmd());
        arch.setSecret(s.getSecret());
        arch.setServerType(s.getServerType() != null ? s.getServerType() : "");
        arch.setUpgrate(s.getUpgrate() != null && s.getUpgrate());
        arch.setBuildUpgrade(s.getBuildUpgrade() != null && s.getBuildUpgrade());
        arch.setServiceName(s.getServiceName() != null ? s.getServiceName() : s.getServiceAlias());
        arch.setK8sComponentName(s.getK8sComponentName() != null ? s.getK8sComponentName() : s.getServiceAlias());
        arch.setExecUser("");
        Optional<ServiceGroupRelation> rel = relationRepo.findByServiceId(s.getServiceId());
        arch.setAppName("");
        arch.setAppId(rel.map(ServiceGroupRelation::getGroupId).orElse(0));
        deleteRepo.save(arch);

        // 3. 清理本地表
        envRepo.findByServiceId(s.getServiceId()).forEach(envRepo::delete);
        portRepo.findByServiceId(s.getServiceId()).forEach(portRepo::delete);
        volumeRepo.findByServiceId(s.getServiceId()).forEach(volumeRepo::delete);
        depRepo.findByServiceId(s.getServiceId()).forEach(depRepo::delete);
        probeRepo.findByServiceId(s.getServiceId()).forEach(probeRepo::delete);
        sourceRepo.deleteByServiceId(s.getServiceId());
        relationRepo.deleteByServiceId(s.getServiceId());
        serviceRepo.delete(s);
    }
}
