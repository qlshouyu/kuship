package cn.kuship.console.modules.team.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.enterprise.entity.TenantEnterprise;
import cn.kuship.console.modules.enterprise.repository.TenantEnterpriseRepository;
import cn.kuship.console.modules.rbac.entity.RoleInfo;
import cn.kuship.console.modules.rbac.repository.RoleInfoRepository;
import cn.kuship.console.modules.rbac.repository.RolePermsRepository;
import cn.kuship.console.modules.rbac.repository.UserRoleRepository;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.repository.PermRelTenantRepository;
import cn.kuship.console.modules.team.repository.TenantsRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 团队生命周期：建（成功/空名/重名/非法 namespace、默认角色+owner）、删（成功/不存在）。 */
class TeamLifecycleServiceTest {

    private final TenantsRepository tenantsRepo = mock(TenantsRepository.class);
    private final TenantEnterpriseRepository entRepo = mock(TenantEnterpriseRepository.class);
    private final PermRelTenantRepository permRel = mock(PermRelTenantRepository.class);
    private final RoleInfoRepository roleRepo = mock(RoleInfoRepository.class);
    private final RolePermsRepository permRepo = mock(RolePermsRepository.class);
    private final UserRoleRepository userRoleRepo = mock(UserRoleRepository.class);
    private final TeamLifecycleService service =
            new TeamLifecycleService(tenantsRepo, entRepo, permRel, roleRepo, permRepo, userRoleRepo);

    private void enterpriseExists() {
        TenantEnterprise e = new TenantEnterprise();
        e.setId(1);
        e.setEnterpriseId("e1");
        when(entRepo.findByEnterpriseId("e1")).thenReturn(Optional.of(e));
        when(tenantsRepo.existsByTenantName(anyString())).thenReturn(false);
        when(tenantsRepo.save(any())).thenAnswer(i -> {
            Tenants t = i.getArgument(0);
            t.setId(9);
            return t;
        });
        AtomicInteger rid = new AtomicInteger(10);
        when(roleRepo.save(any())).thenAnswer(i -> {
            RoleInfo r = i.getArgument(0);
            r.setId(rid.getAndIncrement());
            return r;
        });
    }

    @Test
    void create_team_success_inits_roles_and_owner() {
        enterpriseExists();
        when(tenantsRepo.findByTenantAliasAndEnterpriseId("我的团队", "e1")).thenReturn(Optional.empty());
        Map<String, Object> bean = service.createTeam(700002, "e1", "我的团队", "myns", "");
        assertThat(bean).containsEntry("tenant_alias", "我的团队").containsEntry("creater", 700002)
                .containsEntry("is_active", true).containsEntry("namespace", "myns").containsEntry("limit_memory", 0);
        assertThat((String) bean.get("tenant_id")).hasSize(32);
        assertThat((String) bean.get("tenant_name")).hasSize(8);
        verify(permRel).save(any()); // owner
        verify(roleRepo, times(3)).save(any()); // 3 默认角色
        verify(permRepo, times(3)).saveAll(any()); // 3 角色各自 role_perms
        verify(userRoleRepo).save(any()); // 创建者管理员
    }

    @Test
    void create_team_blank_alias_rejected() {
        assertThatThrownBy(() -> service.createTeam(1, "e1", "  ", "ns", ""))
                .isInstanceOf(ServiceHandleException.class).hasMessageContaining("failed");
        verify(tenantsRepo, never()).save(any());
    }

    @Test
    void create_team_invalid_namespace_rejected() {
        assertThatThrownBy(() -> service.createTeam(1, "e1", "团队", "BadNS", ""))
                .isInstanceOf(ServiceHandleException.class).hasMessageContaining("invalid namespace");
        verify(tenantsRepo, never()).save(any());
    }

    @Test
    void create_team_duplicate_alias_rejected() {
        when(tenantsRepo.findByTenantAliasAndEnterpriseId("dup", "e1")).thenReturn(Optional.of(new Tenants()));
        assertThatThrownBy(() -> service.createTeam(1, "e1", "dup", "ns", ""))
                .isInstanceOf(ServiceHandleException.class).hasMessageContaining("failed");
        verify(tenantsRepo, never()).save(any());
    }

    @Test
    void delete_team_success() {
        Tenants t = new Tenants();
        t.setId(9);
        t.setTenantName("abc");
        when(tenantsRepo.findByTenantNameAndEnterpriseId("abc", "e1")).thenReturn(Optional.of(t));
        service.deleteTeam("abc", "e1");
        verify(permRel).deleteByTenantId(9);
        verify(tenantsRepo).delete(t);
    }

    @Test
    void delete_team_not_found_404() {
        when(tenantsRepo.findByTenantNameAndEnterpriseId("nope", "e1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteTeam("nope", "e1"))
                .isInstanceOf(ServiceHandleException.class)
                .satisfies(e -> assertThat(((ServiceHandleException) e).getStatus()).isEqualTo(404));
        verify(tenantsRepo, never()).delete(any());
    }
}
