package cn.kuship.console.modules.enterprise.service;

import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.account.repository.UserInfoRepository;
import cn.kuship.console.modules.app.entity.ServiceGroup;
import cn.kuship.console.modules.app.repository.ServiceGroupRepository;
import cn.kuship.console.modules.enterprise.entity.TenantEnterprise;
import cn.kuship.console.modules.enterprise.repository.TenantEnterpriseRepository;
import cn.kuship.console.modules.rbac.service.RbacReadService;
import cn.kuship.console.modules.team.entity.PermRelTenant;
import cn.kuship.console.modules.team.entity.TenantRegionInfo;
import cn.kuship.console.modules.team.entity.Tenants;
import cn.kuship.console.modules.team.repository.PermRelTenantRepository;
import cn.kuship.console.modules.team.repository.TenantRegionInfoRepository;
import cn.kuship.console.modules.team.repository.TenantsRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 企业团队概览：active_teams(owner/num/region)、new_join roles 含 owner、无 region 过滤、request 空。 */
class EnterpriseTeamOverviewServiceTest {

    private final TenantEnterpriseRepository entRepo = mock(TenantEnterpriseRepository.class);
    private final PermRelTenantRepository permRel = mock(PermRelTenantRepository.class);
    private final TenantsRepository tenantsRepo = mock(TenantsRepository.class);
    private final TenantRegionInfoRepository regionRepo = mock(TenantRegionInfoRepository.class);
    private final ServiceGroupRepository sgRepo = mock(ServiceGroupRepository.class);
    private final UserInfoRepository userRepo = mock(UserInfoRepository.class);
    private final RbacReadService rbac = mock(RbacReadService.class);
    private final EnterpriseTeamOverviewService service = new EnterpriseTeamOverviewService(
            entRepo, permRel, tenantsRepo, regionRepo, sgRepo, userRepo, rbac);

    @Test
    @SuppressWarnings("unchecked")
    void overview_owner_team() {
        TenantEnterprise e = new TenantEnterprise();
        e.setId(1);
        e.setEnterpriseId("e1");
        when(entRepo.findByEnterpriseId("e1")).thenReturn(Optional.of(e));

        PermRelTenant rel = new PermRelTenant();
        rel.setId(1);
        rel.setUserId(700002);
        rel.setTenantId(1);
        rel.setEnterpriseId(1);
        when(permRel.findByUserId(700002)).thenReturn(List.of(rel));

        Tenants t = new Tenants();
        t.setId(1);
        t.setTenantId("tid");
        t.setTenantName("default");
        t.setTenantAlias("别名");
        t.setCreater(700002);
        t.setEnterpriseId("e1");
        when(tenantsRepo.findByIdIn(List.of(1))).thenReturn(List.of(t));

        TenantRegionInfo tr = new TenantRegionInfo();
        tr.setRegionName("rainbond");
        when(regionRepo.findByTenantId("tid")).thenReturn(List.of(tr));
        when(sgRepo.findByTenantId("tid")).thenReturn(List.of());

        UserInfo owner = new UserInfo();
        owner.setUserId(700002);
        owner.setNickName("interop");
        owner.setRealName("");
        when(userRepo.findById(700002)).thenReturn(Optional.of(owner));

        Map<String, Object> roleItem = new java.util.LinkedHashMap<>();
        roleItem.put("role_id", "1");
        roleItem.put("role_name", "管理员");
        when(rbac.getUserTeamRoles("tid", 700002)).thenReturn(List.of(roleItem));

        Map<String, Object> bean = service.overviewTeam("e1", 700002);
        List<Map<String, Object>> active = (List<Map<String, Object>>) bean.get("active_teams");
        assertThat(active).hasSize(1);
        assertThat(active.get(0)).containsEntry("role", "owner").containsEntry("num", 0)
                .containsEntry("region", "rainbond").containsEntry("owner_name", "interop")
                .containsEntry("team_name", "default");
        List<Map<String, Object>> nj = (List<Map<String, Object>>) bean.get("new_join_team");
        assertThat((List<String>) nj.get(0).get("roles")).containsExactly("管理员", "owner");
        assertThat(nj.get(0)).containsEntry("is_pass", true);
        assertThat((List<?>) bean.get("request_join_team")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void team_without_region_excluded() {
        TenantEnterprise e = new TenantEnterprise();
        e.setId(1);
        e.setEnterpriseId("e1");
        when(entRepo.findByEnterpriseId("e1")).thenReturn(Optional.of(e));
        PermRelTenant rel = new PermRelTenant();
        rel.setId(1);
        rel.setUserId(5);
        rel.setTenantId(2);
        rel.setEnterpriseId(1);
        when(permRel.findByUserId(5)).thenReturn(List.of(rel));
        Tenants t = new Tenants();
        t.setId(2);
        t.setTenantId("t2");
        t.setCreater(9);
        when(tenantsRepo.findByIdIn(List.of(2))).thenReturn(List.of(t));
        when(regionRepo.findByTenantId("t2")).thenReturn(List.of()); // 无 region
        Map<String, Object> bean = service.overviewTeam("e1", 5);
        assertThat((List<?>) bean.get("active_teams")).isEmpty();
        assertThat((List<?>) bean.get("new_join_team")).isEmpty();
    }
}
