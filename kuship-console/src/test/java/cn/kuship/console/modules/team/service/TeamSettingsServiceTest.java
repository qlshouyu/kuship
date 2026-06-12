package cn.kuship.console.modules.team.service;

import cn.kuship.console.common.exception.NoPermissionsException;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.rbac.entity.RoleInfo;
import cn.kuship.console.modules.rbac.repository.RoleInfoRepository;
import cn.kuship.console.modules.rbac.repository.UserRoleRepository;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.repository.PermRelTenantRepository;
import cn.kuship.console.modules.team.repository.TenantsRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 团队设置写：移交(owner-only)、改名(完整 bean)、退出(creater 409 / 成员删两表)。 */
class TeamSettingsServiceTest {

    private final TenantsRepository tenantsRepo = mock(TenantsRepository.class);
    private final PermRelTenantRepository permRel = mock(PermRelTenantRepository.class);
    private final UserRoleRepository userRoleRepo = mock(UserRoleRepository.class);
    private final RoleInfoRepository roleRepo = mock(RoleInfoRepository.class);
    private final TeamSettingsService service =
            new TeamSettingsService(tenantsRepo, permRel, userRoleRepo, roleRepo);

    private static Tenants team(int creater) {
        Tenants t = new Tenants();
        t.setId(1);
        t.setTenantId("t-uuid");
        t.setTenantName("default");
        t.setTenantAlias("orig");
        t.setIsActive(true);
        t.setCreater(creater);
        t.setLimitMemory(0);
        t.setCreateTime(LocalDateTime.of(2026, 6, 12, 21, 9, 0));
        t.setEnterpriseId("e1");
        t.setNamespace("default");
        t.setLogo("");
        return t;
    }

    @Test
    void transfer_owner_sets_creater() {
        Tenants t = team(700002);
        when(tenantsRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        service.transferOwnership(t, 700002, 700003);
        assertThat(t.getCreater()).isEqualTo(700003);
        verify(tenantsRepo).save(t);
    }

    @Test
    void transfer_non_owner_rejected() {
        Tenants t = team(700002);
        assertThatThrownBy(() -> service.transferOwnership(t, 700099, 700003))
                .isInstanceOf(NoPermissionsException.class);
        verify(tenantsRepo, never()).save(any());
    }

    @Test
    void modifyname_returns_full_bean() {
        Tenants t = team(700002);
        when(tenantsRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        Map<String, Object> bean = service.updateTenantInfo(t, "新名", null);
        assertThat(bean).containsOnlyKeys("ID", "tenant_id", "tenant_name", "is_active", "create_time",
                "creater", "limit_memory", "update_time", "tenant_alias", "enterprise_id", "namespace", "logo");
        assertThat(bean).containsEntry("tenant_alias", "新名").containsEntry("is_active", true);
        assertThat(bean.get("update_time")).isNotNull();
    }

    @Test
    void modifyname_blank_alias_keeps_original() {
        Tenants t = team(700002);
        when(tenantsRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        Map<String, Object> bean = service.updateTenantInfo(t, "  ", null);
        assertThat(bean).containsEntry("tenant_alias", "orig");
    }

    @Test
    void exit_creater_rejected_409() {
        Tenants t = team(700002);
        assertThatThrownBy(() -> service.exitTeam(t, 700002))
                .isInstanceOf(ServiceHandleException.class)
                .satisfies(e -> assertThat(((ServiceHandleException) e).getStatus()).isEqualTo(409));
        verify(permRel, never()).deleteByUserIdInAndTenantId(anyList(), any());
    }

    @Test
    void exit_member_deletes_membership_and_roles() {
        Tenants t = team(700002);
        when(roleRepo.findByKindAndKindId("team", "t-uuid")).thenReturn(List.of(roleInfo(1), roleInfo(2)));
        service.exitTeam(t, 700003);
        verify(permRel).deleteByUserIdInAndTenantId(List.of(700003), 1);
        verify(userRoleRepo).deleteByUserIdAndRoleIdIn(eq("700003"), anyList());
    }

    private static RoleInfo roleInfo(int id) {
        RoleInfo r = new RoleInfo();
        r.setId(id);
        r.setKind("team");
        r.setKindId("t-uuid");
        return r;
    }
}
