package cn.kuship.console.modules.account.perm;

/**
 * 权限码常量（与 rainbond {@code console/utils/perms.py} 中的常量一一对应）。
 *
 * <p>本枚举仅声明常用业务权限码（团队 / 应用 / 集群）；后续业务 change 按需新增。完整列表（170+）
 * 在 PermsInfo 表初始化时由 {@code PermsInitService} 写入；本枚举只覆盖常被引用的常量子集。
 */
public final class PermCode {

    private PermCode() {}

    // 团队权限（200xxx）
    public static final String TEAM_INFO = "team_info";
    public static final String TEAM_OVERVIEW_DESCRIBE = "team_overview_describe";
    public static final String TEAM_MEMBER_PERMS = "team_member_perms";
    public static final String TEAM_ROLE_PERMS = "team_role_perms";
    public static final String TEAM_REGION_INSTALL = "team_region_install";
    public static final String TEAM_REGION_DESCRIBE = "team_region_describe";
    public static final String TEAM_PLUGIN_MANAGE = "team_plugin_manage";
    public static final String TEAM_CERTIFICATE = "team_certificate";
    public static final String TEAM_REGISTRY_AUTH = "team_registry_auth";

    // 应用权限（300xxx / 400xxx）
    public static final String APP_OVERVIEW_PERMS = "app_overview_perms";
    public static final String APP_OVERVIEW_DESCRIBE = "app_overview_describe";
    public static final String APP_OVERVIEW_CREATE = "app_overview_create";
    public static final String APP_OVERVIEW_START = "app_overview_start";
    public static final String APP_OVERVIEW_STOP = "app_overview_stop";
    public static final String APP_OVERVIEW_RESTART = "app_overview_restart";
    public static final String APP_OVERVIEW_DEPLOY = "app_overview_deploy";
    public static final String APP_OVERVIEW_DELETE = "app_overview_delete";
    public static final String APP_OVERVIEW_ENV = "app_overview_env";
    public static final String APP_OVERVIEW_TELESCOPIC = "app_overview_telescopic";
    public static final String APP_CREATE_PERMS = "app_create_perms";
    public static final String APP_UPGRADE = "app_upgrade";

    // 企业权限（100xxx）
    public static final String ENTERPRISE_INFO = "enterprise_info";
    public static final String ENTERPRISE_USERS = "enterprise_users";
    public static final String ENTERPRISE_ADMIN = "enterprise_admin";
}
