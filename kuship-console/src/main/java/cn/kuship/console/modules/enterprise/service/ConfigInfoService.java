package cn.kuship.console.modules.enterprise.service;

import cn.kuship.console.modules.account.repository.UserInfoRepository;
import cn.kuship.console.modules.enterprise.entity.ConsoleSysConfig;
import cn.kuship.console.modules.enterprise.entity.TenantEnterprise;
import cn.kuship.console.modules.enterprise.repository.ConsoleSysConfigRepository;
import cn.kuship.console.modules.enterprise.repository.TenantEnterpriseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 平台级公开配置（对齐 ConfigRUDView(AllowAny) + PlatformConfigService.initialization_or_get_config）。
 * 登录页 bootstrap 依赖此接口（rainbondInfo）。未登录可访问。
 * base 键(IS_PUBLIC/MARKET_URL/ENTERPRISE_CENTER_OAUTH/VERSION/IS_USER_REGISTER/OAUTH_SERVICES) 用计算值；
 * cfg 键用 console_sys_config 的 DB 值；末尾追加 env 扁平字段。
 */
@Service
public class ConfigInfoService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** cfg 键（按 PlatformConfigService.cfg_keys 顺序）。 */
    private static final List<String> CFG_KEYS = List.of(
            "TITLE", "LOGO", "LOGIN_IMAGE", "FAVICON", "IS_REGIST", "IS_ALARM", "DOCUMENT",
            "OFFICIAL_DEMO", "CAPTCHA_CODE", "HEADER_COLOR", "HEADER_WRITING_COLOR",
            "SIDEBAR_COLOR", "SIDEBAR_WRITING_COLOR", "FOOTER", "SHADOW",
            "ENTERPRISE_EDITION", "SECURITY_RESTRICTIONS");

    private static final List<String> BASE_KEYS = List.of(
            "IS_PUBLIC", "MARKET_URL", "ENTERPRISE_CENTER_OAUTH", "VERSION", "IS_USER_REGISTER", "OAUTH_SERVICES");

    private final ConsoleSysConfigRepository configRepository;
    private final UserInfoRepository userInfoRepository;
    private final TenantEnterpriseRepository enterpriseRepository;

    public ConfigInfoService(ConsoleSysConfigRepository configRepository,
                             UserInfoRepository userInfoRepository,
                             TenantEnterpriseRepository enterpriseRepository) {
        this.configRepository = configRepository;
        this.userInfoRepository = userInfoRepository;
        this.enterpriseRepository = enterpriseRepository;
    }

    public Map<String, Object> getConfigInfo() {
        List<String> all = new java.util.ArrayList<>(BASE_KEYS);
        all.addAll(CFG_KEYS);
        Map<String, ConsoleSysConfig> byKey = configRepository.findByKeyIn(all).stream()
                .collect(Collectors.toMap(ConsoleSysConfig::getKey, c -> c, (a, b) -> a));

        Map<String, Object> bean = new LinkedHashMap<>();
        // base 键：enable 取 DB，value 用计算/默认值（对齐 base_cfg_keys_value 覆盖）
        boolean userExists = userInfoRepository.count() > 0;
        bean.put("is_public", entry(enable(byKey, "IS_PUBLIC"), false));
        bean.put("market_url", entry(enable(byKey, "MARKET_URL"), dbValue(byKey, "MARKET_URL")));
        bean.put("enterprise_center_oauth", entry(enable(byKey, "ENTERPRISE_CENTER_OAUTH"), null));
        bean.put("version", entry(enable(byKey, "VERSION"), dbValue(byKey, "VERSION")));
        bean.put("is_user_register", entry(enable(byKey, "IS_USER_REGISTER"), userExists));
        bean.put("oauth_services", entry(enable(byKey, "OAUTH_SERVICES"), parsed(byKey, "OAUTH_SERVICES")));
        // cfg 键：enable + value 均取 DB（json eval）
        for (String key : CFG_KEYS) {
            ConsoleSysConfig c = byKey.get(key);
            bean.put(key.toLowerCase(), entry(c != null && Boolean.TRUE.equals(c.getEnable()), parsed(byKey, key)));
        }
        // env / 计算扁平字段（对齐 initialization_or_get_config 尾部 + ConfigRUDView）
        bean.put("default_market_url", "");
        bean.put("disable_logo", false);
        bean.put("enterprise_id", enterpriseRepository.findAll().stream()
                .map(TenantEnterprise::getEnterpriseId).findFirst().orElse(""));
        bean.put("is_disable_logout", false);
        bean.put("is_offline", false);
        bean.put("sso_enable", false);
        bean.put("diy", true);
        bean.put("enable_yum_oauth", false);
        bean.put("diy_customer", "rainbond");
        bean.put("is_delivery_version", false);
        bean.put("portal_site", "");
        return bean;
    }

    private static Map<String, Object> entry(boolean enable, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enable", enable);
        m.put("value", value);
        return m;
    }

    private static boolean enable(Map<String, ConsoleSysConfig> byKey, String key) {
        ConsoleSysConfig c = byKey.get(key);
        return c == null || Boolean.TRUE.equals(c.getEnable()); // 默认 true
    }

    private static String dbValue(Map<String, ConsoleSysConfig> byKey, String key) {
        ConsoleSysConfig c = byKey.get(key);
        return c == null ? null : c.getValue();
    }

    private static Object parsed(Map<String, ConsoleSysConfig> byKey, String key) {
        ConsoleSysConfig c = byKey.get(key);
        if (c == null || c.getValue() == null) {
            return null;
        }
        if (!"json".equalsIgnoreCase(c.getType())) {
            return c.getValue();
        }
        String json = c.getValue().replaceAll("\\bNone\\b", "null")
                .replaceAll("\\bTrue\\b", "true").replaceAll("\\bFalse\\b", "false").replace('\'', '"');
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }
}
