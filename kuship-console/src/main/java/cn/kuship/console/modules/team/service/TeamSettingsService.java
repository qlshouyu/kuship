package cn.kuship.console.modules.team.service;

import cn.kuship.console.common.exception.NoPermissionsException;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.rbac.entity.RoleInfo;
import cn.kuship.console.modules.rbac.repository.RoleInfoRepository;
import cn.kuship.console.modules.rbac.repository.UserRoleRepository;
import cn.kuship.console.modules.team.entity.PermRelTenant;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.repository.PermRelTenantRepository;
import cn.kuship.console.modules.team.repository.TenantsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 团队设置写（对齐 rainbond UserPemTraView / TeamNameModView / TeamExitView）：移交管理权、改名、退出。
 * 均纯 console 库写，不触 region。
 */
@Service
public class TeamSettingsService {

    private static final String KIND_TEAM = "team";
    /** 时间戳序列化格式，对齐 7070（与 TeamReadService 一致）。 */
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TenantsRepository tenantsRepository;
    private final PermRelTenantRepository permRelTenantRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleInfoRepository roleInfoRepository;

    public TeamSettingsService(TenantsRepository tenantsRepository,
                               PermRelTenantRepository permRelTenantRepository,
                               UserRoleRepository userRoleRepository,
                               RoleInfoRepository roleInfoRepository) {
        this.tenantsRepository = tenantsRepository;
        this.permRelTenantRepository = permRelTenantRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleInfoRepository = roleInfoRepository;
    }

    /** 移交管理权（owner-only）：非创建者抛无权；否则 creater→targetUserId。 */
    public void transferOwnership(Tenants team, Integer requesterUserId, Integer targetUserId) {
        if (team.getCreater() == null || !team.getCreater().equals(requesterUserId)) {
            throw new NoPermissionsException(); // 对齐 TeamOwnerView owner-only
        }
        team.setCreater(targetUserId);
        tenantsRepository.save(team);
    }

    /** 改名：alias 非空才更新、logo 非空才更新；刷新 update_time；返回完整 bean（to_dict 12 字段）。 */
    public Map<String, Object> updateTenantInfo(Tenants team, String newAlias, String newLogo) {
        if (newAlias != null && !newAlias.isBlank()) {
            team.setTenantAlias(newAlias);
        }
        if (newLogo != null && !newLogo.isBlank()) {
            team.setLogo(newLogo);
        }
        team.setUpdateTime(LocalDateTime.now());
        Tenants saved = tenantsRepository.save(team);
        return toDict(saved);
    }

    /** 退出团队：创建者不可退（409）；否则事务删自身 tenant_perms + 本团队 user_role。 */
    @Transactional
    public void exitTeam(Tenants team, Integer userId) {
        if (team.getCreater() != null && team.getCreater().equals(userId)) {
            throw new ServiceHandleException(409, "not allow exit.", "您是当前团队创建者，不能退出此团队");
        }
        permRelTenantRepository.deleteByUserIdInAndTenantId(List.of(userId), team.getId());
        List<String> teamRoleIds = roleInfoRepository.findByKindAndKindId(KIND_TEAM, team.getTenantId()).stream()
                .map(r -> String.valueOf(r.getId())).collect(Collectors.toList());
        if (!teamRoleIds.isEmpty()) {
            userRoleRepository.deleteByUserIdAndRoleIdIn(String.valueOf(userId), teamRoleIds);
        }
    }

    /** 团队完整 to_dict（字段顺序对齐 7070）。 */
    private Map<String, Object> toDict(Tenants t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ID", t.getId());
        m.put("tenant_id", t.getTenantId());
        m.put("tenant_name", t.getTenantName());
        m.put("is_active", t.getIsActive());
        m.put("create_time", t.getCreateTime() == null ? null : t.getCreateTime().format(TS));
        m.put("creater", t.getCreater());
        m.put("limit_memory", t.getLimitMemory());
        m.put("update_time", t.getUpdateTime() == null ? null : t.getUpdateTime().format(TS));
        m.put("tenant_alias", t.getTenantAlias());
        m.put("enterprise_id", t.getEnterpriseId());
        m.put("namespace", t.getNamespace());
        m.put("logo", t.getLogo());
        return m;
    }
}
