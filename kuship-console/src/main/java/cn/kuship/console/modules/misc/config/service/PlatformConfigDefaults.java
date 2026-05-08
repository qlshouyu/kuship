package cn.kuship.console.modules.misc.config.service;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站点平台配置默认值表，复刻 rainbond {@code services/config_service.py::PlatformConfigService}
 * 的 {@code cfg_keys_value}（17 项）+ {@code base_cfg_keys_value}（5 项）。
 *
 * <p>{@link #BASE_CFG_DEFAULTS} 中部分 value 依赖运行时 env，必须通过
 * {@link #getEffectiveBase(String, Environment)} 获取，不能直接读 Map.
 */
@Component
public class PlatformConfigDefaults {

    public record DefaultEntry(Object value, String desc, boolean enable, String type) {
    }

    public static final Map<String, DefaultEntry> CFG_DEFAULTS = buildCfgDefaults();
    public static final Map<String, DefaultEntry> BASE_CFG_DEFAULTS = buildBaseCfgDefaults();

    /** rainbond {@code initialization_or_get_config} 中只切 enable 不改 value 的 key。 */
    public static final List<String> BASE_CFG_KEYS = List.copyOf(BASE_CFG_DEFAULTS.keySet());
    /** 普通 cfg key（可改 value+enable，DELETE 时重置）。 */
    public static final List<String> CFG_KEYS = List.copyOf(CFG_DEFAULTS.keySet());

    private static Map<String, DefaultEntry> buildBaseCfgDefaults() {
        Map<String, DefaultEntry> m = new LinkedHashMap<>();
        m.put("IS_PUBLIC", new DefaultEntry(false, "是否是Cloud", true, "string"));
        m.put("MARKET_URL", new DefaultEntry("http://api.goodrain.com:80", "商店路由", true, "string"));
        m.put("ENTERPRISE_CENTER_OAUTH", new DefaultEntry(null, "enterprise center oauth 配置", true, "string"));
        m.put("VERSION", new DefaultEntry("public-cloud", "平台版本", true, "string"));
        m.put("IS_USER_REGISTER", new DefaultEntry(false, "开启/关闭OAuthServices功能", true, "string"));
        m.put("OAUTH_SERVICES", new DefaultEntry(List.of(), "开启/关闭OAuthServices功能", true, "json"));
        return Map.copyOf(m);
    }

    private static Map<String, DefaultEntry> buildCfgDefaults() {
        Map<String, DefaultEntry> m = new LinkedHashMap<>();
        m.put("TITLE", new DefaultEntry("", "Rainbond web tile", true, "string"));
        m.put("LOGO", new DefaultEntry(null, "Rainbond Logo url", true, "string"));
        m.put("FAVICON", new DefaultEntry(null, "Rainbond web favicon url", true, "string"));
        m.put("LOGIN_IMAGE", new DefaultEntry(null, "Rainbond web login_image url", true, "string"));
        m.put("DOCUMENT", new DefaultEntry(
                Map.of("platform_url", "https://www.rainbond.com/"),
                "开启/关闭文档", true, "json"));
        m.put("OFFICIAL_DEMO", new DefaultEntry(null, "开启/关闭官方Demo", true, "string"));
        m.put("IS_REGIST", new DefaultEntry(null, "是否允许注册", true, "string"));
        m.put("IS_ALARM", new DefaultEntry(null, "是否展示报警", false, "string"));
        m.put("CAPTCHA_CODE", new DefaultEntry(null, "开启/关闭登录验证码", false, "string"));
        m.put("HEADER_COLOR", new DefaultEntry("", "头部颜色配置", true, "string"));
        m.put("HEADER_WRITING_COLOR", new DefaultEntry("", "头部文字颜色配置", true, "string"));
        m.put("SIDEBAR_COLOR", new DefaultEntry("", "侧边栏颜色配置", true, "string"));
        m.put("SIDEBAR_WRITING_COLOR", new DefaultEntry("", "侧边栏文字颜色配置", true, "string"));
        m.put("FOOTER", new DefaultEntry("", "footer", true, "string"));
        m.put("SHADOW", new DefaultEntry(null, "控制阴影", true, "string"));
        m.put("ENTERPRISE_EDITION", new DefaultEntry("true", "是否是企业版", true, "string"));
        m.put("SECURITY_RESTRICTIONS", new DefaultEntry(null, "是否启用安全限制，默认不启用", false, "string"));
        return Map.copyOf(m);
    }

    /**
     * 处理依赖运行时 env 的 base_cfg key。仅 GET 路径调用，PUT/DELETE 不依赖。
     */
    public DefaultEntry getEffectiveBase(String key, Environment env) {
        DefaultEntry raw = BASE_CFG_DEFAULTS.get(key);
        if (raw == null) return null;
        return switch (key) {
            case "IS_PUBLIC" -> new DefaultEntry(envBool(env, "IS_PUBLIC", false), raw.desc(), raw.enable(), raw.type());
            case "MARKET_URL" -> new DefaultEntry(env.getProperty("GOODRAIN_APP_API", "http://api.goodrain.com:80"),
                    raw.desc(), raw.enable(), raw.type());
            case "VERSION" -> new DefaultEntry(env.getProperty("RELEASE_DESC", "public-cloud"),
                    raw.desc(), raw.enable(), raw.type());
            case "IS_USER_REGISTER" -> new DefaultEntry(envBool(env, "IS_PUBLIC", false),
                    raw.desc(), raw.enable(), raw.type());
            default -> raw;
        };
    }

    static boolean envBool(Environment env, String key, boolean fallback) {
        String v = env.getProperty(key);
        if (v == null || v.isBlank()) return fallback;
        return v.equalsIgnoreCase("true") || v.equals("1");
    }
}
