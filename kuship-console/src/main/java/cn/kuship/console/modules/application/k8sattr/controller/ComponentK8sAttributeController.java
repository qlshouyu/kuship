package cn.kuship.console.modules.application.k8sattr.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.k8sattr.api.K8sAttributeOperations;
import cn.kuship.console.modules.application.k8sattr.entity.ComponentK8sAttribute;
import cn.kuship.console.modules.application.k8sattr.repository.ComponentK8sAttributeRepository;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code /console/teams/{team_name}/apps/{service_alias}/k8s-attributes*}：组件 k8s 属性 CRUD（双写本地 + region）。 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps/{service_alias}/k8s-attributes")
public class ComponentK8sAttributeController {

    private final K8sAttributeOperations attrOps;
    private final ComponentK8sAttributeRepository attrRepo;
    private final TenantsRepository tenantsRepo;
    private final TenantServiceRepository serviceRepo;

    public ComponentK8sAttributeController(K8sAttributeOperations attrOps,
                                            ComponentK8sAttributeRepository attrRepo,
                                            TenantsRepository tenantsRepo,
                                            TenantServiceRepository serviceRepo) {
        this.attrOps = attrOps;
        this.attrRepo = attrRepo;
        this.tenantsRepo = tenantsRepo;
        this.serviceRepo = serviceRepo;
    }

    @GetMapping
    public ApiResult listAttributes(@PathVariable("team_name") String teamName,
                                     @PathVariable("service_alias") String serviceAlias) {
        TenantService s = requireService(teamName, serviceAlias);
        List<ComponentK8sAttribute> rows = attrRepo.findByComponentId(s.getServiceId());
        return GeneralMessage.okList(rows.stream().map(ComponentK8sAttributeController::serialize).toList());
    }

    @PostMapping
    @Transactional
    public ApiResult create(@PathVariable("team_name") String teamName,
                             @PathVariable("service_alias") String serviceAlias,
                             @RequestBody Map<String, Object> body) {
        TenantService s = requireService(teamName, serviceAlias);
        Map<String, Object> attr = extractAttribute(body);
        String name = stringOrThrow(attr, "name", "属性名为空");
        String saveType = stringOrDefault(attr, "save_type", "yaml");
        String value = stringOrDefault(attr, "attribute_value", "");

        if (attrRepo.findByComponentIdAndName(s.getServiceId(), name).isPresent()) {
            throw new ServiceHandleException(409, "k8s attribute name exists", "属性名已存在");
        }
        ComponentK8sAttribute e = new ComponentK8sAttribute();
        e.setTenantId(s.getTenantId());
        e.setComponentId(s.getServiceId());
        e.setName(name);
        e.setSaveType(saveType);
        e.setAttributeValue(value);
        attrRepo.save(e);

        attrOps.createK8sAttribute(s.getServiceRegion(), teamName, serviceAlias, body);
        return GeneralMessage.ok(serialize(e));
    }

    @GetMapping("/{name}")
    public ApiResult getOne(@PathVariable("team_name") String teamName,
                             @PathVariable("service_alias") String serviceAlias,
                             @PathVariable("name") String name) {
        TenantService s = requireService(teamName, serviceAlias);
        ComponentK8sAttribute local = attrRepo.findByComponentIdAndName(s.getServiceId(), name)
                .orElseThrow(() -> new ServiceHandleException(404, "k8s attribute not found", "属性不存在"));
        return GeneralMessage.ok(serialize(local));
    }

    @PutMapping("/{name}")
    @Transactional
    public ApiResult update(@PathVariable("team_name") String teamName,
                             @PathVariable("service_alias") String serviceAlias,
                             @PathVariable("name") String name,
                             @RequestBody Map<String, Object> body) {
        TenantService s = requireService(teamName, serviceAlias);
        Map<String, Object> attr = extractAttribute(body);
        Object attrName = attr.get("name");
        if (attrName != null && !name.equals(attrName)) {
            throw new ServiceHandleException(400, "name mismatch", "参数错误");
        }
        ComponentK8sAttribute e = attrRepo.findByComponentIdAndName(s.getServiceId(), name)
                .orElseThrow(() -> new ServiceHandleException(404, "k8s attribute not found", "属性不存在"));
        if (attr.containsKey("attribute_value")) {
            e.setAttributeValue(attr.get("attribute_value") == null ? "" : attr.get("attribute_value").toString());
        }
        if (attr.containsKey("save_type")) {
            Object st = attr.get("save_type");
            if (st != null) e.setSaveType(st.toString());
        }
        attrRepo.save(e);

        attrOps.updateK8sAttribute(s.getServiceRegion(), teamName, serviceAlias, body);
        return GeneralMessage.ok(serialize(e));
    }

    @DeleteMapping("/{name}")
    @Transactional
    public ApiResult delete(@PathVariable("team_name") String teamName,
                             @PathVariable("service_alias") String serviceAlias,
                             @PathVariable("name") String name) {
        TenantService s = requireService(teamName, serviceAlias);
        try {
            attrOps.deleteK8sAttribute(s.getServiceRegion(), teamName, serviceAlias, Map.of("name", name));
        } catch (cn.kuship.console.infrastructure.region.exception.RegionApiException ex) {
            if (ex.getHttpStatus() != 404) {
                throw ex;
            }
        }
        attrRepo.deleteByComponentIdAndName(s.getServiceId(), name);
        return GeneralMessage.ok();
    }

    private TenantService requireService(String teamName, String serviceAlias) {
        Tenants team = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        return serviceRepo.findByTenantIdAndServiceAlias(team.getTenantId(), serviceAlias)
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractAttribute(Map<String, Object> body) {
        if (body == null) return Map.of();
        Object inner = body.get("attribute");
        if (inner instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return body;
    }

    private static String stringOrThrow(Map<String, Object> map, String key, String msgShow) {
        Object v = map.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new ServiceHandleException(400, key + " is empty", msgShow);
        }
        return v.toString();
    }

    private static String stringOrDefault(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v == null ? def : v.toString();
    }

    private static Map<String, Object> serialize(ComponentK8sAttribute e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("tenant_id", e.getTenantId());
        m.put("component_id", e.getComponentId());
        m.put("name", e.getName());
        m.put("save_type", e.getSaveType());
        m.put("attribute_value", e.getAttributeValue());
        m.put("create_time", e.getCreateTime());
        m.put("update_time", e.getUpdateTime());
        return m;
    }
}
