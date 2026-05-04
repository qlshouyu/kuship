package cn.kuship.console.modules.application.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.entity.ServiceGroup;
import cn.kuship.console.modules.application.entity.ServiceGroupRelation;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.ServiceGroupRelationRepository;
import cn.kuship.console.modules.application.repository.ServiceGroupRepository;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** {@code /console/teams/{team_name}/apps/{service_alias}}：组件查询 + 归属切换。 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps/{service_alias}")
public class ComponentController {

    private final TenantServiceRepository serviceRepo;
    private final ServiceGroupRelationRepository relationRepo;
    private final ServiceGroupRepository groupRepo;

    public ComponentController(TenantServiceRepository serviceRepo,
                                 ServiceGroupRelationRepository relationRepo,
                                 ServiceGroupRepository groupRepo) {
        this.serviceRepo = serviceRepo;
        this.relationRepo = relationRepo;
        this.groupRepo = groupRepo;
    }

    private TenantService requireService(String teamName, String serviceAlias) {
        // tenant_id 校验留给上层 RBAC + 链路追踪；本端点按 service_alias 全局唯一查找
        return serviceRepo.findAll().stream()
                .filter(s -> serviceAlias.equals(s.getServiceAlias()))
                .findFirst()
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
    }

    @GetMapping(value = {"/detail", "/detail/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult detail(@PathVariable("team_name") String teamName,
                              @PathVariable("service_alias") String serviceAlias) {
        return GeneralMessage.ok(serializeDetail(requireService(teamName, serviceAlias)));
    }

    @GetMapping(value = {"/brief", "/brief/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult brief(@PathVariable("team_name") String teamName,
                             @PathVariable("service_alias") String serviceAlias) {
        return GeneralMessage.ok(serializeBrief(requireService(teamName, serviceAlias)));
    }

    @GetMapping(value = {"/keyword", "/keyword/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult keyword(@PathVariable("team_name") String teamName,
                               @PathVariable("service_alias") String serviceAlias) {
        TenantService s = requireService(teamName, serviceAlias);
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("service_alias", s.getServiceAlias());
        bean.put("service_cname", s.getServiceCname());
        bean.put("k8s_component_name", s.getK8sComponentName());
        return GeneralMessage.ok(bean);
    }

    @GetMapping(value = {"/group", "/group/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult getGroup(@PathVariable("team_name") String teamName,
                                @PathVariable("service_alias") String serviceAlias) {
        TenantService s = requireService(teamName, serviceAlias);
        Optional<ServiceGroupRelation> rel = relationRepo.findByServiceId(s.getServiceId());
        if (rel.isEmpty()) {
            return GeneralMessage.ok(Map.of("app_id", null));
        }
        Optional<ServiceGroup> g = groupRepo.findById(rel.get().getGroupId());
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("app_id", rel.get().getGroupId());
        g.ifPresent(group -> {
            bean.put("group_name", group.getGroupName());
            bean.put("k8s_app", group.getK8sApp());
        });
        return GeneralMessage.ok(bean);
    }

    @PutMapping(value = {"/group", "/group/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    @Transactional
    public ApiResult migrateGroup(@PathVariable("team_name") String teamName,
                                     @PathVariable("service_alias") String serviceAlias,
                                     @RequestBody Map<String, Object> body) {
        TenantService s = requireService(teamName, serviceAlias);
        Object appIdObj = body.get("app_id");
        if (!(appIdObj instanceof Number n)) {
            throw new ServiceHandleException(400, "app_id required", "缺少 app_id 参数");
        }
        Integer newAppId = n.intValue();
        ServiceGroup target = groupRepo.findById(newAppId)
                .orElseThrow(() -> new ServiceHandleException(404, "target application not found", "目标应用不存在"));
        relationRepo.deleteByServiceId(s.getServiceId());
        ServiceGroupRelation rel = new ServiceGroupRelation();
        rel.setServiceId(s.getServiceId());
        rel.setGroupId(newAppId);
        rel.setTenantId(s.getTenantId());
        rel.setRegionName(target.getRegionName());
        relationRepo.save(rel);
        return GeneralMessage.ok();
    }

    private Map<String, Object> serializeBrief(TenantService s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("service_id", s.getServiceId());
        m.put("service_alias", s.getServiceAlias());
        m.put("service_cname", s.getServiceCname());
        m.put("k8s_component_name", s.getK8sComponentName());
        m.put("image", s.getImage());
        m.put("version", s.getVersion());
        m.put("create_status", s.getCreateStatus());
        return m;
    }

    private Map<String, Object> serializeDetail(TenantService s) {
        Map<String, Object> m = serializeBrief(s);
        m.put("tenant_id", s.getTenantId());
        m.put("service_region", s.getServiceRegion());
        m.put("category", s.getCategory());
        m.put("service_port", s.getServicePort());
        m.put("is_web_service", s.getWebService());
        m.put("update_version", s.getUpdateVersion());
        m.put("cmd", s.getCmd());
        m.put("min_node", s.getMinNode());
        m.put("min_cpu", s.getMinCpu());
        m.put("min_memory", s.getMinMemory());
        m.put("extend_method", s.getExtendMethod());
        m.put("language", s.getLanguage());
        m.put("build_strategy", s.getBuildStrategy());
        m.put("protocol", s.getProtocol());
        m.put("namespace", s.getNamespace());
        m.put("port_type", s.getPortType());
        m.put("service_origin", s.getServiceOrigin());
        m.put("service_source", s.getServiceSource());
        m.put("service_name", s.getServiceName());
        m.put("server_type", s.getServerType());
        m.put("arch", s.getArch());
        m.put("create_time", s.getCreateTime());
        m.put("update_time", s.getUpdateTime());
        m.put("desc", s.getDescription());
        // 脱敏
        String secret = s.getSecret();
        m.put("secret", secret == null || secret.isBlank() ? null : "***" + secret.length() + " chars***");
        return m;
    }
}
