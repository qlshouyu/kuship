package cn.kuship.console.modules.misc.config.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.account.repository.UserInfoRepository;
import cn.kuship.console.modules.misc.config.entity.ConsoleSysConfig;
import cn.kuship.console.modules.misc.config.repository.ConsoleSysConfigRepository;
import cn.kuship.console.modules.misc.config.service.PlatformConfigDefaults.DefaultEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站点平台配置 service。复刻 rainbond {@code services/config_service.py::PlatformConfigService}
 * 与 {@code views/logos.py::ConfigRUDView} 的合并逻辑：
 *
 * <ul>
 *   <li>{@link #initializationOrGetConfig()} 既懒初始化默认值入库，又汇总 22 个 cfg key + 11 个顶层 env 字段</li>
 *   <li>{@link #updateConfig(String, Object, boolean)} 按 base_cfg / cfg 分支更新</li>
 *   <li>{@link #deleteConfig(String)} 仅 cfg_keys 允许；重置为默认值</li>
 * </ul>
 */
@Service
public class PlatformConfigService {

    private static final Logger log = LoggerFactory.getLogger(PlatformConfigService.class);

    private final ConsoleSysConfigRepository repo;
    private final PlatformConfigDefaults defaults;
    private final Environment env;
    private final ObjectMapper objectMapper;
    private final UserInfoRepository userInfoRepository;

    public PlatformConfigService(ConsoleSysConfigRepository repo,
                                 PlatformConfigDefaults defaults,
                                 Environment env,
                                 ObjectMapper objectMapper,
                                 UserInfoRepository userInfoRepository) {
        this.repo = repo;
        this.defaults = defaults;
        this.env = env;
        this.objectMapper = objectMapper;
        this.userInfoRepository = userInfoRepository;
    }

    @Transactional
    public Map<String, Object> initializationOrGetConfig() {
        Map<String, Object> result = new LinkedHashMap<>();

        for (String key : PlatformConfigDefaults.BASE_CFG_KEYS) {
            DefaultEntry def = defaults.getEffectiveBase(key, env);
            ConsoleSysConfig row = upsertDefault(key, def);
            // base_cfg：value 始终用 effective default（rainbond Python 行为：DB.value 被覆盖）
            // 例外：IS_USER_REGISTER 动态查 user_info 表（rainbond config_service.is_user_register()），
            //   有任何用户 → true（UI 跳 login）；空表 → false（UI 跳 register 引导初始化管理员）
            Object effectiveValue = "IS_USER_REGISTER".equals(key)
                    ? userInfoRepository.count() > 0
                    : def.value();
            result.put(key.toLowerCase(), enableValueEntry(row.getEnable(), effectiveValue));
        }

        for (String key : PlatformConfigDefaults.CFG_KEYS) {
            DefaultEntry def = PlatformConfigDefaults.CFG_DEFAULTS.get(key);
            ConsoleSysConfig row = upsertDefault(key, def);
            Object value = parseValue(row.getValue(), row.getType(), def.value());
            result.put(key.toLowerCase(), enableValueEntry(row.getEnable(), value));
        }

        // 顶层平铺（rainbond ConfigRUDView.get + ConfigService.initialization_or_get_config）
        boolean isPublic = PlatformConfigDefaults.envBool(env, "IS_PUBLIC", false);
        if (PlatformConfigDefaults.envBool(env, "IS_ENTERPRISE_EDITION", false)) {
            result.put("enterprise_edition", enableValueEntry(true, "true"));
        }
        result.put("default_market_url", env.getProperty("DEFAULT_APP_MARKET_URL", ""));
        result.put("disable_logo", "true".equalsIgnoreCase(env.getProperty("DISABLE_LOGO")));
        result.put("enterprise_id", env.getProperty("ENTERPRISE_ID", ""));
        result.put("is_disable_logout", PlatformConfigDefaults.envBool(env, "IS_DISABLE_LOGOUT", false));
        result.put("is_offline", PlatformConfigDefaults.envBool(env, "IS_OFFLINE", false));
        result.put("sso_enable", PlatformConfigDefaults.envBool(env, "SSO_ENABLE", false));
        result.put("diy", !"false".equalsIgnoreCase(env.getProperty("DIY", "true")));
        result.put("enable_yum_oauth", env.getProperty("ENABLE_YUM_OAUTH") != null
                && !env.getProperty("ENABLE_YUM_OAUTH", "").isBlank());
        result.put("diy_customer", env.getProperty("DIY_CUSTOMER", "rainbond"));
        result.put("is_delivery_version", env.getProperty("IS_DELIVERY_VERSION") != null
                && !env.getProperty("IS_DELIVERY_VERSION", "").isBlank());
        result.put("portal_site", env.getProperty("PORTAL_SITE", ""));
        if (env.getProperty("USE_SAAS") != null && !env.getProperty("USE_SAAS", "").isBlank()) {
            result.put("is_saas", true);
        }
        // 占位，等 IS_PUBLIC 真值反映在 base_cfg
        if (!result.containsKey("is_public")) {
            result.put("is_public", enableValueEntry(true, isPublic));
        }
        return result;
    }

    private static Map<String, Object> enableValueEntry(Boolean enable, Object value) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("enable", enable != null && enable);
        entry.put("value", value);
        return entry;
    }

    @Transactional
    public Map<String, Object> updateConfig(String key, Object value, boolean enable) {
        if (key == null || key.isBlank()) {
            throw new ServiceHandleException(404, "no found config key", "更新失败");
        }
        String upper = key.toUpperCase();

        if (PlatformConfigDefaults.BASE_CFG_KEYS.contains(upper)) {
            ConsoleSysConfig row = repo.findByKey(upper)
                    .orElseGet(() -> upsertDefault(upper, defaults.getEffectiveBase(upper, env)));
            row.setEnable(enable);
            repo.save(row);
            DefaultEntry def = defaults.getEffectiveBase(upper, env);
            return Map.of(upper.toLowerCase(), enableValueEntry(enable, def.value()));
        }

        if (!PlatformConfigDefaults.CFG_KEYS.contains(upper)) {
            throw new ServiceHandleException(404, "no found config key", "更新失败");
        }

        if (value == null) {
            throw new ServiceHandleException(404, "no found config value", "更新失败");
        }

        DefaultEntry def = PlatformConfigDefaults.CFG_DEFAULTS.get(upper);
        ConsoleSysConfig row = repo.findByKey(upper).orElseGet(() -> upsertDefault(upper, def));
        String storedType;
        String storedValue;
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            storedType = "json";
            try {
                storedValue = objectMapper.writeValueAsString(value);
            } catch (RuntimeException e) {
                throw new ServiceHandleException(500, "json serialize failed", "更新失败", e);
            }
        } else {
            storedType = "string";
            storedValue = String.valueOf(value);
        }
        row.setValue(storedValue);
        row.setType(storedType);
        row.setEnable(enable);
        repo.save(row);
        return Map.of(upper.toLowerCase(), enableValueEntry(enable, value));
    }

    @Transactional
    public Map<String, Object> deleteConfig(String key) {
        if (key == null || key.isBlank()) {
            throw new ServiceHandleException(404, "no found config key", "重置失败");
        }
        String upper = key.toUpperCase();
        if (!PlatformConfigDefaults.CFG_KEYS.contains(upper)) {
            throw new ServiceHandleException(404, "can not delete key value", "该配置不可重置");
        }
        DefaultEntry def = PlatformConfigDefaults.CFG_DEFAULTS.get(upper);
        ConsoleSysConfig row = repo.findByKey(upper).orElseGet(() -> upsertDefault(upper, def));
        row.setValue(serializeDefault(def));
        row.setType(def.type());
        row.setDesc(def.desc());
        row.setEnable(def.enable());
        repo.save(row);
        return Map.of(upper.toLowerCase(), enableValueEntry(def.enable(), def.value()));
    }

    private ConsoleSysConfig upsertDefault(String key, DefaultEntry def) {
        return repo.findByKey(key).orElseGet(() -> insertDefault(key, def));
    }

    private ConsoleSysConfig insertDefault(String key, DefaultEntry def) {
        ConsoleSysConfig c = new ConsoleSysConfig();
        c.setKey(key);
        c.setType(def.type());
        c.setValue(serializeDefault(def));
        c.setDesc(def.desc());
        c.setEnable(def.enable());
        c.setCreateTime(LocalDateTime.now());
        c.setEnterpriseId("");
        try {
            return repo.saveAndFlush(c);
        } catch (DataIntegrityViolationException race) {
            // rainbond-console 与 kuship-console 并发首次写入时退化为读
            return repo.findByKey(key).orElseThrow(() -> race);
        }
    }

    private String serializeDefault(DefaultEntry def) {
        if (def.value() == null) return null;
        if ("json".equals(def.type())) {
            try {
                return objectMapper.writeValueAsString(def.value());
            } catch (RuntimeException e) {
                log.warn("[PlatformConfig] failed to serialize default for type=json, fallback to toString", e);
                return String.valueOf(def.value());
            }
        }
        return String.valueOf(def.value());
    }

    /**
     * 按 type 解析 DB 落库的 value。容错：rainbond 历史 type=json 的 value 用 Python repr
     * 写为单引号格式（如 {@code {'platform_url':'...'}}），先尝试合规 JSON，失败再单转双重试。
     */
    Object parseValue(String stored, String type, Object fallback) {
        if (stored == null) return null;
        if (!"json".equals(type)) return stored;
        try {
            return objectMapper.readValue(stored, Object.class);
        } catch (RuntimeException e1) {
            String coerced = stored.replace('\'', '"');
            try {
                return objectMapper.readValue(coerced, Object.class);
            } catch (RuntimeException e2) {
                log.warn("[PlatformConfig] json value unparsable, fallback to default: stored={}", stored);
                return fallback;
            }
        }
    }

}
