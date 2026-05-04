package cn.kuship.console.modules.region.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.infrastructure.region.api.TenantOperations;
import cn.kuship.console.infrastructure.region.api.dto.CreateTenantReq;
import cn.kuship.console.modules.account.entity.TenantRegionInfo;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantRegionInfoRepository;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.region.entity.RegionInfo;
import cn.kuship.console.modules.region.repository.RegionInfoEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** team-region 关联（开通/关闭/查询）。 */
@Service
public class TeamRegionService {

    private static final Logger log = LoggerFactory.getLogger(TeamRegionService.class);

    private final TenantsRepository tenantsRepo;
    private final TenantRegionInfoRepository tenantRegionRepo;
    private final RegionInfoEntityRepository regionRepo;
    private final TenantOperations tenantOperations;

    public TeamRegionService(TenantsRepository tenantsRepo,
                              TenantRegionInfoRepository tenantRegionRepo,
                              RegionInfoEntityRepository regionRepo,
                              TenantOperations tenantOperations) {
        this.tenantsRepo = tenantsRepo;
        this.tenantRegionRepo = tenantRegionRepo;
        this.regionRepo = regionRepo;
        this.tenantOperations = tenantOperations;
    }

    public List<RegionInfo> listOpened(String tenantName) {
        Tenants team = requireTeam(tenantName);
        Set<String> opened = tenantRegionRepo.findByTenantId(team.getTenantId()).stream()
                .filter(tr -> Boolean.TRUE.equals(tr.getActive()))
                .map(TenantRegionInfo::getRegionName)
                .collect(Collectors.toSet());
        return regionRepo.findByEnterpriseId(team.getEnterpriseId()).stream()
                .filter(r -> opened.contains(r.getRegionName()))
                .toList();
    }

    public List<RegionInfo> listUnopened(String tenantName) {
        Tenants team = requireTeam(tenantName);
        Set<String> opened = new HashSet<>(tenantRegionRepo.findByTenantId(team.getTenantId()).stream()
                .filter(tr -> Boolean.TRUE.equals(tr.getActive()))
                .map(TenantRegionInfo::getRegionName)
                .toList());
        return regionRepo.findByEnterpriseId(team.getEnterpriseId()).stream()
                .filter(r -> !opened.contains(r.getRegionName()))
                .toList();
    }

    @Transactional
    public boolean openRegion(String tenantName, String regionName) {
        Tenants team = requireTeam(tenantName);
        RegionInfo region = regionRepo.findByEnterpriseIdAndRegionName(team.getEnterpriseId(), regionName)
                .orElseThrow(() -> new ServiceHandleException(404, "region not found", "集群不存在"));
        // 幂等
        if (tenantRegionRepo.findByTenantIdAndRegionName(team.getTenantId(), regionName)
                .filter(tr -> Boolean.TRUE.equals(tr.getActive())).isPresent()) {
            log.debug("team {} already opened region {}", tenantName, regionName);
            return false;
        }

        // 调 region API 在集群侧建 namespace（先调远程，再写本地表 → 失败不留垃圾）
        try {
            tenantOperations.createTenant(regionName, team.getEnterpriseId(),
                    new CreateTenantReq(team.getTenantName(), team.getTenantId(),
                            team.getEnterpriseId(), team.getNamespace(), false));
        } catch (Exception ex) {
            log.warn("region {} createTenant failed: {}", regionName, ex.getMessage());
            throw ex;
        }

        TenantRegionInfo tr = new TenantRegionInfo();
        tr.setTenantId(team.getTenantId());
        tr.setRegionName(regionName);
        tr.setActive(true);
        tr.setInit(true);
        tr.setServiceStatus(1);
        tr.setEnterpriseId(team.getEnterpriseId());
        tr.setRegionTenantName(team.getTenantName());
        tr.setRegionTenantId(team.getTenantId());
        tr.setRegionScope(region.getScope());
        tr.setCreateTime(LocalDateTime.now());
        tr.setUpdateTime(LocalDateTime.now());
        tenantRegionRepo.save(tr);
        return true;
    }

    private Tenants requireTeam(String tenantName) {
        return tenantsRepo.findByTenantName(tenantName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
    }
}
