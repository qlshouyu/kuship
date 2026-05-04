package cn.kuship.console.modules.application.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.dto.CreateGroupReq;
import cn.kuship.console.modules.application.dto.UpdateGroupReq;
import cn.kuship.console.modules.application.entity.ServiceGroup;
import cn.kuship.console.modules.application.entity.ServiceGroupRelation;
import cn.kuship.console.modules.application.repository.ServiceGroupRelationRepository;
import cn.kuship.console.modules.application.repository.ServiceGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class GroupService {

    private static final Set<String> ALLOWED_GOVERNANCE_MODES = Set.of(
            "KUBERNETES_NATIVE", "BUILD_IN_SERVICE_MESH", "RAINBOND_NATIVE_SERVICE_MESH", "NO_GOVERNANCE");

    private final ServiceGroupRepository repo;
    private final ServiceGroupRelationRepository relationRepo;
    private final TenantsRepository tenantsRepo;

    public GroupService(ServiceGroupRepository repo,
                         ServiceGroupRelationRepository relationRepo,
                         TenantsRepository tenantsRepo) {
        this.repo = repo;
        this.relationRepo = relationRepo;
        this.tenantsRepo = tenantsRepo;
    }

    public List<ServiceGroup> listByTeam(String teamName) {
        Tenants team = requireTeam(teamName);
        return repo.findByTenantId(team.getTenantId());
    }

    public ServiceGroup get(Integer appId) {
        return repo.findById(appId)
                .orElseThrow(() -> new ServiceHandleException(404, "application not found", "应用不存在"));
    }

    @Transactional
    public ServiceGroup create(String teamName, CreateGroupReq req) {
        Tenants team = requireTeam(teamName);
        if (req.governanceMode() != null && !ALLOWED_GOVERNANCE_MODES.contains(req.governanceMode())) {
            throw new ServiceHandleException(400, "invalid governance_mode", "治理模式取值非法");
        }
        ServiceGroup g = new ServiceGroup();
        g.setGroupName(req.groupName());
        g.setNote(req.note());
        g.setRegionName(req.regionName());
        g.setTenantId(team.getTenantId());
        g.setIsDefault(false);
        g.setOrderIndex(0);
        g.setAppType("rainbond");
        g.setGovernanceMode(req.governanceMode() != null ? req.governanceMode() : "KUBERNETES_NATIVE");
        g.setK8sApp(req.k8sApp() != null ? req.k8sApp() : req.groupName());
        g.setCreateTime(LocalDateTime.now());
        g.setUpdateTime(LocalDateTime.now());
        return repo.save(g);
    }

    @Transactional
    public ServiceGroup update(Integer appId, UpdateGroupReq req) {
        ServiceGroup g = get(appId);
        if (req.governanceMode() != null) {
            if (!ALLOWED_GOVERNANCE_MODES.contains(req.governanceMode())) {
                throw new ServiceHandleException(400, "invalid governance_mode", "治理模式取值非法");
            }
            g.setGovernanceMode(req.governanceMode());
        }
        if (req.groupName() != null) g.setGroupName(req.groupName());
        if (req.note() != null) g.setNote(req.note());
        if (req.k8sApp() != null) g.setK8sApp(req.k8sApp());
        if (req.logo() != null) g.setLogo(req.logo());
        g.setUpdateTime(LocalDateTime.now());
        return repo.save(g);
    }

    @Transactional
    public void delete(Integer appId) {
        ServiceGroup g = get(appId);
        if (!relationRepo.findByGroupId(appId).isEmpty()) {
            throw new ServiceHandleException(400, "application has components",
                    "该应用下仍有组件，请先迁移或删除组件");
        }
        repo.delete(g);
    }

    public List<ServiceGroupRelation> componentRelations(Integer appId) {
        return relationRepo.findByGroupId(appId);
    }

    public String governanceMode(Integer appId) {
        return get(appId).getGovernanceMode();
    }

    @Transactional
    public ServiceGroup setGovernanceMode(Integer appId, String mode) {
        if (!ALLOWED_GOVERNANCE_MODES.contains(mode)) {
            throw new ServiceHandleException(400, "invalid governance_mode", "治理模式取值非法");
        }
        ServiceGroup g = get(appId);
        g.setGovernanceMode(mode);
        g.setUpdateTime(LocalDateTime.now());
        return repo.save(g);
    }

    private Tenants requireTeam(String teamName) {
        return tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
    }
}
