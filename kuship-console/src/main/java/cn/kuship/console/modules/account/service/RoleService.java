package cn.kuship.console.modules.account.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.account.dto.CreateRoleReq;
import cn.kuship.console.modules.account.dto.UpdateRolePermsReq;
import cn.kuship.console.modules.account.dto.UpdateUserRolesReq;
import cn.kuship.console.modules.account.entity.PermsInfo;
import cn.kuship.console.modules.account.entity.RoleInfo;
import cn.kuship.console.modules.account.entity.RolePerms;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.entity.UserRole;
import cn.kuship.console.modules.account.repository.PermsInfoRepository;
import cn.kuship.console.modules.account.repository.RoleInfoRepository;
import cn.kuship.console.modules.account.repository.RolePermsRepository;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.account.repository.UserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class RoleService {

    private final RoleInfoRepository roleRepo;
    private final RolePermsRepository rolePermsRepo;
    private final PermsInfoRepository permsInfoRepo;
    private final UserRoleRepository userRoleRepo;
    private final TenantsRepository tenantsRepo;

    public RoleService(RoleInfoRepository roleRepo,
                        RolePermsRepository rolePermsRepo,
                        PermsInfoRepository permsInfoRepo,
                        UserRoleRepository userRoleRepo,
                        TenantsRepository tenantsRepo) {
        this.roleRepo = roleRepo;
        this.rolePermsRepo = rolePermsRepo;
        this.permsInfoRepo = permsInfoRepo;
        this.userRoleRepo = userRoleRepo;
        this.tenantsRepo = tenantsRepo;
    }

    private Tenants requireTeam(String tenantName) {
        return tenantsRepo.findByTenantName(tenantName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
    }

    public List<RoleInfo> listTeamRoles(String tenantName) {
        Tenants team = requireTeam(tenantName);
        return roleRepo.findByKindAndKindId("team", String.valueOf(team.getId()));
    }

    @Transactional
    public RoleInfo createRole(String tenantName, CreateRoleReq req) {
        Tenants team = requireTeam(tenantName);
        if (roleRepo.findByNameAndKindAndKindId(req.name(), "team", String.valueOf(team.getId())).isPresent()) {
            throw new ServiceHandleException(400, "role name exists", "角色名已存在");
        }
        RoleInfo role = new RoleInfo();
        role.setName(req.name());
        role.setKind("team");
        role.setKindId(String.valueOf(team.getId()));
        RoleInfo saved = roleRepo.save(role);
        if (req.permCodes() != null) {
            applyPerms(saved.getId(), req.permCodes());
        }
        return saved;
    }

    @Transactional
    public RoleInfo updateRole(String tenantName, Integer roleId, String newName) {
        requireTeam(tenantName);
        RoleInfo role = roleRepo.findById(roleId)
                .orElseThrow(() -> new ServiceHandleException(404, "role not found", "角色不存在"));
        if (newName != null && !newName.isBlank()) {
            role.setName(newName);
        }
        return roleRepo.save(role);
    }

    @Transactional
    public void deleteRole(String tenantName, Integer roleId) {
        requireTeam(tenantName);
        rolePermsRepo.deleteByRoleId(roleId);
        roleRepo.deleteById(roleId);
    }

    @Transactional
    public void updateRolePerms(String tenantName, Integer roleId, UpdateRolePermsReq req) {
        requireTeam(tenantName);
        rolePermsRepo.deleteByRoleId(roleId);
        applyPerms(roleId, req.permCodes());
    }

    private void applyPerms(Integer roleId, List<String> permCodes) {
        List<RolePerms> rows = new ArrayList<>();
        for (String code : permCodes) {
            permsInfoRepo.findByName(code).ifPresent(info -> {
                RolePerms rp = new RolePerms();
                rp.setRoleId(roleId);
                rp.setPermCode(info.getCode());
                rp.setAppId(-1);
                rows.add(rp);
            });
        }
        rolePermsRepo.saveAll(rows);
    }

    public List<RolePerms> rolePerms(Integer roleId) {
        return rolePermsRepo.findByRoleId(roleId);
    }

    public List<UserRole> userRoles(Integer userId) {
        return userRoleRepo.findByUserId(String.valueOf(userId));
    }

    @Transactional
    public void replaceUserRoles(Integer userId, UpdateUserRolesReq req) {
        userRoleRepo.deleteAllByUserId(String.valueOf(userId));
        for (Integer rid : req.roleIds()) {
            UserRole ur = new UserRole();
            ur.setUserId(String.valueOf(userId));
            ur.setRoleId(String.valueOf(rid));
            userRoleRepo.save(ur);
        }
    }

    @Transactional
    public void removeUserRoles(Integer userId, List<Integer> roleIds) {
        userRoleRepo.deleteByUserIdAndRoleIds(String.valueOf(userId),
                roleIds.stream().map(String::valueOf).toList());
    }

    public List<PermsInfo> allPerms() {
        return permsInfoRepo.findAll();
    }
}
