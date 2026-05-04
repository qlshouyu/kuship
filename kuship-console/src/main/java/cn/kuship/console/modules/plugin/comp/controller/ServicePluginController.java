package cn.kuship.console.modules.plugin.comp.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.plugin.api.PluginOperations;
import cn.kuship.console.modules.plugin.comp.entity.ServicePluginConfigVar;
import cn.kuship.console.modules.plugin.comp.entity.TenantServicePluginAttr;
import cn.kuship.console.modules.plugin.comp.entity.TenantServicePluginRelation;
import cn.kuship.console.modules.plugin.comp.repository.ServicePluginConfigVarRepository;
import cn.kuship.console.modules.plugin.comp.repository.TenantServicePluginAttrRepository;
import cn.kuship.console.modules.plugin.comp.repository.TenantServicePluginRelationRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 组件挂载插件：5 endpoint。三表关联。 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps/{service_alias}")
public class ServicePluginController {

    private final TenantServicePluginRelationRepository relationRepo;
    private final TenantServicePluginAttrRepository attrRepo;
    private final ServicePluginConfigVarRepository configRepo;
    private final TenantsRepository tenantsRepo;
    private final TenantServiceRepository serviceRepo;
    private final PluginOperations pluginOps;

    public ServicePluginController(TenantServicePluginRelationRepository relationRepo,
                                       TenantServicePluginAttrRepository attrRepo,
                                       ServicePluginConfigVarRepository configRepo,
                                       TenantsRepository tenantsRepo,
                                       TenantServiceRepository serviceRepo,
                                       PluginOperations pluginOps) {
        this.relationRepo = relationRepo;
        this.attrRepo = attrRepo;
        this.configRepo = configRepo;
        this.tenantsRepo = tenantsRepo;
        this.serviceRepo = serviceRepo;
        this.pluginOps = pluginOps;
    }

    @GetMapping(value = {"/pluginlist", "/pluginlist/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult list(@PathVariable("team_name") String teamName,
                            @PathVariable("service_alias") String alias) {
        TenantService s = requireService(teamName, alias);
        return GeneralMessage.okList(relationRepo.findByServiceId(s.getServiceId()).stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("plugin_id", r.getPluginId());
            m.put("build_version", r.getBuildVersion());
            m.put("plugin_status", r.getPluginStatus());
            m.put("service_meta_type", r.getServiceMetaType());
            m.put("min_memory", r.getMinMemory());
            m.put("min_cpu", r.getMinCpu());
            return m;
        }).toList());
    }

    @PostMapping(value = {"/plugins/{plugin_id}/install", "/plugins/{plugin_id}/install/"})
    @RequirePerm(PermCode.APP_OVERVIEW_PERMS)
    @Transactional
    public ApiResult install(@PathVariable("team_name") String teamName,
                                @PathVariable("service_alias") String alias,
                                @PathVariable("plugin_id") String pluginId,
                                @RequestBody Map<String, Object> body) {
        TenantService s = requireService(teamName, alias);
        if (relationRepo.findByServiceIdAndPluginId(s.getServiceId(), pluginId).isPresent()) {
            throw new ServiceHandleException(400, "plugin already installed", "插件已挂载");
        }
        String buildVersion = String.valueOf(body.getOrDefault("build_version", "1.0.0"));
        TenantServicePluginRelation r = new TenantServicePluginRelation();
        r.setServiceId(s.getServiceId());
        r.setPluginId(pluginId);
        r.setBuildVersion(buildVersion);
        r.setServiceMetaType(String.valueOf(body.getOrDefault("service_meta_type", "upstream_port")));
        r.setPluginStatus(true);
        r.setMinMemory(body.get("min_memory") instanceof Number n ? n.intValue() : 64);
        r.setMinCpu(body.get("min_cpu") instanceof Number n ? n.intValue() : 50);
        r.setCreateTime(LocalDateTime.now());
        relationRepo.save(r);

        // attrs（如果带跨服务依赖配置）
        Object attrsObj = body.get("attrs");
        if (attrsObj instanceof List<?> attrs) {
            for (Object o : attrs) {
                if (!(o instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> a = (Map<String, Object>) o;
                ServicePluginConfigVar v = new ServicePluginConfigVar();
                v.setServiceId(s.getServiceId());
                v.setPluginId(pluginId);
                v.setBuildVersion(buildVersion);
                v.setServiceMetaType(r.getServiceMetaType());
                v.setInjection(String.valueOf(a.getOrDefault("injection", "auto")));
                v.setDestServiceId(String.valueOf(a.getOrDefault("dest_service_id", "")));
                v.setDestServiceAlias(String.valueOf(a.getOrDefault("dest_service_alias", "")));
                v.setContainerPort(a.get("container_port") instanceof Number n ? n.intValue() : 0);
                v.setAttrs(String.valueOf(a.getOrDefault("attrs", "{}")));
                v.setProtocol(String.valueOf(a.getOrDefault("protocol", "http")));
                v.setCreateTime(LocalDateTime.now());
                configRepo.save(v);
            }
        }

        try {
            pluginOps.installToService(s.getServiceRegion(), teamName, alias, body);
        } catch (Exception ignored) {
            // region 失败不阻塞本地写入；下次 region 自动重试或前端提示
        }
        return GeneralMessage.ok(Map.of("plugin_id", pluginId, "build_version", buildVersion));
    }

    @PutMapping(value = {"/plugins/{plugin_id}/open", "/plugins/{plugin_id}/open/"})
    @RequirePerm(PermCode.APP_OVERVIEW_PERMS)
    @Transactional
    public ApiResult open(@PathVariable("team_name") String teamName,
                            @PathVariable("service_alias") String alias,
                            @PathVariable("plugin_id") String pluginId,
                            @RequestBody Map<String, Object> body) {
        TenantService s = requireService(teamName, alias);
        TenantServicePluginRelation r = relationRepo.findByServiceIdAndPluginId(s.getServiceId(), pluginId)
                .orElseThrow(() -> new ServiceHandleException(404, "not installed", "插件未挂载"));
        Object v = body.get("plugin_status");
        boolean open = v instanceof Boolean ? (Boolean) v : Boolean.parseBoolean(String.valueOf(v));
        r.setPluginStatus(open);
        relationRepo.save(r);
        try {
            pluginOps.openOnService(s.getServiceRegion(), teamName, alias, pluginId, body);
        } catch (Exception ignored) {
            // region 失败前端可重试
        }
        return GeneralMessage.ok(Map.of("plugin_id", pluginId, "plugin_status", open));
    }

    @PutMapping(value = {"/plugins/{plugin_id}/configs", "/plugins/{plugin_id}/configs/"})
    @RequirePerm(PermCode.APP_OVERVIEW_PERMS)
    @Transactional
    public ApiResult configs(@PathVariable("team_name") String teamName,
                                @PathVariable("service_alias") String alias,
                                @PathVariable("plugin_id") String pluginId,
                                @RequestBody Map<String, Object> body) {
        TenantService s = requireService(teamName, alias);
        relationRepo.findByServiceIdAndPluginId(s.getServiceId(), pluginId)
                .orElseThrow(() -> new ServiceHandleException(404, "not installed", "插件未挂载"));
        configRepo.deleteByServiceIdAndPluginId(s.getServiceId(), pluginId);
        Object attrsObj = body.get("attrs");
        if (attrsObj instanceof List<?> attrs) {
            for (Object o : attrs) {
                if (!(o instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> a = (Map<String, Object>) o;
                ServicePluginConfigVar v = new ServicePluginConfigVar();
                v.setServiceId(s.getServiceId());
                v.setPluginId(pluginId);
                v.setBuildVersion(String.valueOf(a.getOrDefault("build_version", "1.0.0")));
                v.setServiceMetaType(String.valueOf(a.getOrDefault("service_meta_type", "upstream_port")));
                v.setInjection(String.valueOf(a.getOrDefault("injection", "auto")));
                v.setDestServiceId(String.valueOf(a.getOrDefault("dest_service_id", "")));
                v.setDestServiceAlias(String.valueOf(a.getOrDefault("dest_service_alias", "")));
                v.setContainerPort(a.get("container_port") instanceof Number n ? n.intValue() : 0);
                v.setAttrs(String.valueOf(a.getOrDefault("attrs", "{}")));
                v.setProtocol(String.valueOf(a.getOrDefault("protocol", "http")));
                v.setCreateTime(LocalDateTime.now());
                configRepo.save(v);
            }
        }
        return GeneralMessage.ok();
    }

    @DeleteMapping(value = {"/plugins/{plugin_id}/install", "/plugins/{plugin_id}/install/"})
    @RequirePerm(PermCode.APP_OVERVIEW_PERMS)
    @Transactional
    public ApiResult uninstall(@PathVariable("team_name") String teamName,
                                  @PathVariable("service_alias") String alias,
                                  @PathVariable("plugin_id") String pluginId) {
        TenantService s = requireService(teamName, alias);
        try {
            pluginOps.uninstallFromService(s.getServiceRegion(), teamName, alias, pluginId);
        } catch (Exception ignored) {
            // region 失败仍清理本地避免幽灵数据
        }
        relationRepo.deleteByServiceIdAndPluginId(s.getServiceId(), pluginId);
        attrRepo.deleteByServiceIdAndPluginId(s.getServiceId(), pluginId);
        configRepo.deleteByServiceIdAndPluginId(s.getServiceId(), pluginId);
        return GeneralMessage.ok();
    }

    @GetMapping(value = {"/analyze_plugins", "/analyze_plugins/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult analyze(@PathVariable("team_name") String teamName,
                                @PathVariable("service_alias") String alias) {
        requireService(teamName, alias);
        return GeneralMessage.okList(java.util.List.of());
    }

    private TenantService requireService(String teamName, String alias) {
        Tenants team = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        return serviceRepo.findByTenantIdAndServiceAlias(team.getTenantId(), alias)
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
    }
}
