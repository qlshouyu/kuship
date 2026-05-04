package cn.kuship.console.modules.account.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.util.UuidGenerator;
import cn.kuship.console.modules.account.dto.AddTeamMembersReq;
import cn.kuship.console.modules.account.dto.CreateTeamReq;
import cn.kuship.console.modules.account.dto.UpdateTeamReq;
import cn.kuship.console.modules.account.entity.PermRelTenant;
import cn.kuship.console.modules.account.entity.TenantEnterprise;
import cn.kuship.console.modules.account.entity.TenantRegionInfo;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.PermRelTenantRepository;
import cn.kuship.console.modules.account.repository.TenantEnterpriseRepository;
import cn.kuship.console.modules.account.repository.TenantRegionInfoRepository;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TeamService {

    private final TenantsRepository tenantsRepo;
    private final TenantEnterpriseRepository enterpriseRepo;
    private final TenantRegionInfoRepository tenantRegionRepo;
    private final PermRelTenantRepository permRelRepo;

    public TeamService(TenantsRepository tenantsRepo,
                        TenantEnterpriseRepository enterpriseRepo,
                        TenantRegionInfoRepository tenantRegionRepo,
                        PermRelTenantRepository permRelRepo) {
        this.tenantsRepo = tenantsRepo;
        this.enterpriseRepo = enterpriseRepo;
        this.tenantRegionRepo = tenantRegionRepo;
        this.permRelRepo = permRelRepo;
    }

    @Transactional
    public Tenants createTeam(Integer creatorUserId, String creatorEnterpriseId, CreateTeamReq req) {
        if (creatorUserId == null) {
            throw new ServiceHandleException(401, "missing user", "未认证或 token 失效");
        }
        if (tenantsRepo.findByTenantName(req.teamName()).isPresent()) {
            throw new ServiceHandleException(400, "team_name exists", "团队名称已被占用");
        }
        String namespace = req.namespace() == null || req.namespace().isBlank()
                ? req.teamName() : req.namespace();
        if (tenantsRepo.findByNamespace(namespace).isPresent()) {
            throw new ServiceHandleException(400, "namespace exists", "命名空间已被占用");
        }
        String entId = req.enterpriseId() != null && !req.enterpriseId().isBlank()
                ? req.enterpriseId() : creatorEnterpriseId;
        TenantEnterprise enterprise = enterpriseRepo.findByEnterpriseId(entId)
                .orElseThrow(() -> new ServiceHandleException(400, "enterprise not found", "企业不存在"));

        Tenants team = new Tenants();
        team.setTenantId(UuidGenerator.makeTenantId());
        team.setTenantName(req.teamName());
        team.setTenantAlias(req.teamAlias() != null ? req.teamAlias() : req.teamName());
        team.setNamespace(namespace);
        team.setEnterpriseId(entId);
        team.setCreater(creatorUserId);
        team.setActive(true);
        team.setLimitMemory(1024);
        team.setCreateTime(LocalDateTime.now());
        team.setUpdateTime(LocalDateTime.now());
        Tenants saved = tenantsRepo.save(team);

        // 团队-集群绑定
        if (req.useableRegions() != null && !req.useableRegions().isBlank()) {
            for (String region : req.useableRegions().split(",")) {
                String r = region.trim();
                if (r.isEmpty()) continue;
                TenantRegionInfo tr = new TenantRegionInfo();
                tr.setTenantId(saved.getTenantId());
                tr.setRegionName(r);
                tr.setActive(true);
                tr.setInit(false);
                tr.setServiceStatus(1);
                tr.setEnterpriseId(entId);
                tr.setCreateTime(LocalDateTime.now());
                tr.setUpdateTime(LocalDateTime.now());
                tenantRegionRepo.save(tr);
            }
        }

        // 创建人加入 team（owner）
        PermRelTenant rel = new PermRelTenant();
        rel.setUserId(creatorUserId);
        rel.setTenantId(saved.getId());
        rel.setIdentity("owner");
        rel.setEnterpriseId(enterprise.getId());
        permRelRepo.save(rel);
        return saved;
    }

    public Tenants requireTeam(String tenantName) {
        return tenantsRepo.findByTenantName(tenantName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
    }

    @Transactional
    public Tenants updateTeam(String tenantName, Integer actorUserId, UpdateTeamReq req) {
        Tenants team = requireTeam(tenantName);
        if (!actorUserId.equals(team.getCreater())) {
            throw new ServiceHandleException(403, "not team owner", "无该团队管理权限");
        }
        if (req.teamAlias() != null) team.setTenantAlias(req.teamAlias());
        if (req.logo() != null) team.setLogo(req.logo());
        team.setUpdateTime(LocalDateTime.now());
        return tenantsRepo.save(team);
    }

    @Transactional
    public void deleteTeam(String tenantName, Integer actorUserId, boolean isSysAdmin) {
        Tenants team = requireTeam(tenantName);
        if (!isSysAdmin && !actorUserId.equals(team.getCreater())) {
            throw new ServiceHandleException(403, "not team owner", "无该团队管理权限");
        }
        // 删除 tenant_perms / tenant_region 关联
        for (PermRelTenant rel : permRelRepo.findByTenantId(team.getId())) {
            permRelRepo.delete(rel);
        }
        for (TenantRegionInfo tr : tenantRegionRepo.findByTenantId(team.getTenantId())) {
            tenantRegionRepo.delete(tr);
        }
        tenantsRepo.delete(team);
    }

    @Transactional
    public void exitTeam(String tenantName, Integer userId) {
        Tenants team = requireTeam(tenantName);
        List<PermRelTenant> all = permRelRepo.findByTenantId(team.getId());
        boolean isOwner = userId.equals(team.getCreater());
        long ownersCount = all.stream().filter(r -> "owner".equals(r.getIdentity())).count();
        if (isOwner && ownersCount <= 1) {
            throw new ServiceHandleException(400, "only owner cannot exit", "团队仅剩一位 owner，无法退出");
        }
        permRelRepo.deleteByUserIdAndTenantId(userId, team.getId());
    }

    @Transactional
    public void addMembers(String tenantName, AddTeamMembersReq req) {
        Tenants team = requireTeam(tenantName);
        TenantEnterprise enterprise = enterpriseRepo.findByEnterpriseId(team.getEnterpriseId())
                .orElseThrow(() -> new ServiceHandleException(400, "enterprise not found", "企业不存在"));
        for (Integer userId : req.userIds()) {
            if (permRelRepo.existsByUserIdAndTenantId(userId, team.getId())) {
                continue;
            }
            PermRelTenant rel = new PermRelTenant();
            rel.setUserId(userId);
            rel.setTenantId(team.getId());
            rel.setIdentity("developer");
            rel.setEnterpriseId(enterprise.getId());
            if (req.roleIds() != null && !req.roleIds().isEmpty()) {
                rel.setRoleId(req.roleIds().get(0));
            }
            permRelRepo.save(rel);
        }
    }

    @Transactional
    public void removeMembers(String tenantName, List<Integer> userIds) {
        Tenants team = requireTeam(tenantName);
        permRelRepo.deleteByUserIdsAndTenantId(userIds, team.getId());
    }

    @Transactional
    public void transferOwner(String tenantName, Integer fromUserId, Integer toUserId) {
        Tenants team = requireTeam(tenantName);
        if (!fromUserId.equals(team.getCreater())) {
            throw new ServiceHandleException(403, "not team owner", "仅 owner 可转让");
        }
        if (!permRelRepo.existsByUserIdAndTenantId(toUserId, team.getId())) {
            throw new ServiceHandleException(400, "target user not in team", "目标用户不在团队中");
        }
        team.setCreater(toUserId);
        team.setUpdateTime(LocalDateTime.now());
        tenantsRepo.save(team);
        permRelRepo.findByUserIdAndTenantId(toUserId, team.getId()).ifPresent(rel -> {
            rel.setIdentity("owner");
            permRelRepo.save(rel);
        });
    }

    public List<PermRelTenant> teamMembers(String tenantName) {
        Tenants team = requireTeam(tenantName);
        return permRelRepo.findByTenantId(team.getId());
    }
}
