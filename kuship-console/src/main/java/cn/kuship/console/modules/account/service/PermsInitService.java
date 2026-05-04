package cn.kuship.console.modules.account.service;

import cn.kuship.console.modules.account.entity.PermsInfo;
import cn.kuship.console.modules.account.repository.PermsInfoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 启动时确保 perms_info 表中包含 {@link PermCodeRegistry} 中声明的所有权限码（按 name 去重 upsert）。
 *
 * <p>本 change 仅声明业务最常用的权限码子集；后续业务 change 按需新增。
 */
@Service
public class PermsInitService {

    private static final Logger log = LoggerFactory.getLogger(PermsInitService.class);

    private final PermsInfoRepository repo;

    public PermsInitService(PermsInfoRepository repo) {
        this.repo = repo;
    }

    public record PermDef(String name, String description, int code, String group, String kind) {}

    private static final List<PermDef> SEED = List.of(
            // ---- enterprise ----
            new PermDef("enterprise_info", "企业视图", 100000, "admin", "enterprise"),
            new PermDef("enterprise_users", "企业用户管理", 100002, "admin", "enterprise"),
            new PermDef("enterprise_admin", "企业管理员", 100099, "admin", "enterprise"),
            // ---- team ----
            new PermDef("team_info", "团队信息", 200000, "owner", "team"),
            new PermDef("team_overview_describe", "团队概览查看", 200001, "viewer", "team"),
            new PermDef("team_member_perms", "团队成员管理", 200010, "admin", "team"),
            new PermDef("team_role_perms", "团队角色管理", 200011, "admin", "team"),
            new PermDef("team_region_install", "团队集群开通", 200020, "owner", "team"),
            new PermDef("team_region_describe", "团队集群查看", 200021, "viewer", "team"),
            new PermDef("team_plugin_manage", "团队插件管理", 200030, "developer", "team"),
            new PermDef("team_certificate", "团队证书管理", 200040, "developer", "team"),
            new PermDef("team_registry_auth", "团队镜像仓库授权", 200050, "developer", "team"),
            // ---- app ----
            new PermDef("app_overview_perms", "应用概览权限", 300000, "developer", "app"),
            new PermDef("app_overview_describe", "应用查看", 300001, "viewer", "app"),
            new PermDef("app_overview_create", "应用创建", 300002, "developer", "app"),
            new PermDef("app_overview_stop", "应用停止", 300003, "developer", "app"),
            new PermDef("app_overview_env", "应用环境变量", 300010, "developer", "app"),
            new PermDef("app_create_perms", "应用创建权限", 300020, "developer", "app"),
            new PermDef("app_upgrade", "应用升级", 300030, "developer", "app")
    );

    @Transactional
    public int runInit() {
        int upserted = 0;
        for (PermDef def : SEED) {
            Optional<PermsInfo> existing = repo.findByName(def.name());
            if (existing.isPresent()) {
                PermsInfo info = existing.get();
                info.setCode(def.code());
                info.setDescription(def.description());
                info.setGroup(def.group());
                info.setKind(def.kind());
                repo.save(info);
            } else {
                PermsInfo info = new PermsInfo();
                info.setName(def.name());
                info.setCode(def.code());
                info.setDescription(def.description());
                info.setGroup(def.group());
                info.setKind(def.kind());
                repo.save(info);
            }
            upserted++;
        }
        log.info("PermsInitService: upserted {} perm definitions", upserted);
        return upserted;
    }
}
