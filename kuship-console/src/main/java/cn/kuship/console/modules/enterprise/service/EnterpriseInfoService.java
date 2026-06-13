package cn.kuship.console.modules.enterprise.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.enterprise.entity.ConsoleSysConfig;
import cn.kuship.console.modules.enterprise.entity.TenantEnterprise;
import cn.kuship.console.modules.enterprise.repository.ConsoleSysConfigRepository;
import cn.kuship.console.modules.enterprise.repository.TenantEnterpriseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 企业信息（对齐 rainbond EnterpriseRUDView.get）：tenant_enterprise.to_dict + default_region({}) +
 * EnterpriseConfigService 配置项（console_sys_config 读取，key 全局唯一）。
 */
@Service
public class EnterpriseInfoService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** base_cfg_keys + cfg_keys（对齐 EnterpriseConfigService），值置于 bean 顶层（key.lower()→{enable,value}）。 */
    private static final List<String> CFG_KEYS = List.of(
            "OAUTH_SERVICES",
            "APPSTORE_IMAGE_HUB", "NEWBIE_GUIDE", "EXPORT_APP", "CLOUD_MARKET", "OBJECT_STORAGE", "AUTO_SSL", "TITLE",
            "LOGO", "FAVICON", "LOGIN_IMAGE", "DOCUMENT", "OFFICIAL_DEMO", "VISUAL_MONITOR", "CAPTCHA_CODE",
            "HEADER_COLOR", "HEADER_WRITING_COLOR", "SIDEBAR_COLOR", "SIDEBAR_WRITING_COLOR", "FOOTER", "SHADOW",
            "SHOW_K8S", "SHOW_LANGUE", "SECURITY_RESTRICTIONS");

    private final TenantEnterpriseRepository enterpriseRepository;
    private final ConsoleSysConfigRepository configRepository;

    public EnterpriseInfoService(TenantEnterpriseRepository enterpriseRepository,
                                 ConsoleSysConfigRepository configRepository) {
        this.enterpriseRepository = enterpriseRepository;
        this.configRepository = configRepository;
    }

    public Map<String, Object> info(String enterpriseId) {
        TenantEnterprise e = enterpriseRepository.findByEnterpriseId(enterpriseId)
                .orElseThrow(() -> new ServiceHandleException(404, "enterprise not found", "企业不存在"));

        Map<String, Object> bean = new LinkedHashMap<>();
        // tenant_enterprise.to_dict（create_time 空格格式；logo 列由配置 LOGO 覆盖，此处不单列）
        bean.put("ID", e.getId());
        bean.put("enterprise_id", e.getEnterpriseId());
        bean.put("enterprise_name", e.getEnterpriseName());
        bean.put("enterprise_alias", e.getEnterpriseAlias());
        bean.put("create_time", e.getCreateTime() == null ? null : e.getCreateTime().format(TS));
        bean.put("enterprise_token", e.getEnterpriseToken());
        bean.put("is_active", e.getIsActive());
        bean.put("enable_team_resource_view", Boolean.TRUE.equals(e.getEnableTeamResourceView()));

        bean.put("default_region", new LinkedHashMap<>()); // ENABLE_CLUSTER!=true → {}

        // 配置项：console_sys_config 按 key 读取（全局唯一），key.lower() → {enable, value}
        Map<String, ConsoleSysConfig> byKey = configRepository.findByKeyIn(CFG_KEYS).stream()
                .collect(Collectors.toMap(ConsoleSysConfig::getKey, c -> c, (a, b) -> a));
        for (String key : CFG_KEYS) {
            ConsoleSysConfig c = byKey.get(key);
            if (c == null) {
                continue; // 行缺失（rainbond 会插默认；共享库已全有）
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("enable", Boolean.TRUE.equals(c.getEnable()));
            entry.put("value", parseValue(c.getType(), c.getValue()));
            bean.put(key.toLowerCase(), entry);
        }

        // 特殊字段（env 派生；本环境默认）
        bean.put("default_market_url", ""); // DEFAULT_APP_MARKET_URL 未设
        bean.put("disable_logo", false);    // DISABLE_LOGO != "true"
        return bean;
    }

    /** 配置值解析：json 类型 → Python-repr 转 JSON 后解析为结构；string → NULL→null 否则原串。 */
    private static Object parseValue(String type, String value) {
        if (value == null) {
            return null;
        }
        if (!"json".equalsIgnoreCase(type)) {
            return value; // string（含 ""）
        }
        String json = pythonReprToJson(value);
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (Exception ex) {
            return null;
        }
    }

    /** Python repr → JSON：None→null、True→true、False→false（词边界）、单引号→双引号（值内无嵌套引号）。 */
    private static String pythonReprToJson(String s) {
        return s.replaceAll("\\bNone\\b", "null")
                .replaceAll("\\bTrue\\b", "true")
                .replaceAll("\\bFalse\\b", "false")
                .replace('\'', '"');
    }
}
