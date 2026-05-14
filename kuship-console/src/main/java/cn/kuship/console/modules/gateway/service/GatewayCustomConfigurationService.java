package cn.kuship.console.modules.gateway.service;

import cn.kuship.console.infrastructure.region.api.GatewayOperations;
import cn.kuship.console.modules.gateway.entity.GatewayCustomConfigure;
import cn.kuship.console.modules.gateway.repository.GatewayCustomConfigureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.type.MapType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 网关高级路由配置业务（对齐 rainbond Python {@code domain_service.py:update_http_rule_config}）。
 *
 * <p>写流程（design.md 决策 5）：
 * <ol>
 *   <li>调 region {@code upgradeConfiguration} 下发 ConfigMap</li>
 *   <li>region 成功后写（或更新）{@code gateway_custom_configuration} 本地行</li>
 *   <li>region 失败则本地不写</li>
 * </ol>
 */
@Service
public class GatewayCustomConfigurationService {

    private final GatewayCustomConfigureRepository configRepo;
    private final GatewayOperations gatewayOps;
    private final ObjectMapper json;
    private final MapType mapType;

    public GatewayCustomConfigurationService(GatewayCustomConfigureRepository configRepo,
                                              GatewayOperations gatewayOps,
                                              ObjectMapper json) {
        this.configRepo = configRepo;
        this.gatewayOps = gatewayOps;
        this.json = json;
        this.mapType = json.getTypeFactory()
                .constructMapType(LinkedHashMap.class, String.class, Object.class);
    }

    /**
     * 获取高级路由配置（反序列化 JSON → Map）。
     */
    public Map<String, Object> getValue(String ruleId) {
        Optional<GatewayCustomConfigure> opt = configRepo.findByRuleId(ruleId);
        if (opt.isEmpty() || opt.get().getValue() == null) {
            return Map.of();
        }
        String raw = opt.get().getValue();
        if (raw.isBlank()) return Map.of();
        return json.readValue(raw, mapType);
    }

    /**
     * 写入 / 更新高级路由配置（先 region → 后本地）。
     */
    @Transactional
    public void setValue(String regionName, String enterpriseId, String tenantName,
                          String ruleId, Map<String, Object> configMap) {
        // 1. 调 region 下发（失败则不写本地）
        gatewayOps.upgradeConfiguration(regionName, enterpriseId, tenantName, ruleId, configMap);

        // 2. 序列化写本地
        String jsonStr = json.writeValueAsString(configMap);
        GatewayCustomConfigure cfg = configRepo.findByRuleId(ruleId)
                .orElseGet(() -> {
                    GatewayCustomConfigure c = new GatewayCustomConfigure();
                    c.setRuleId(ruleId);
                    return c;
                });
        cfg.setValue(jsonStr);
        configRepo.save(cfg);
    }
}
