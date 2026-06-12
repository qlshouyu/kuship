package cn.kuship.console.modules.rbac.perms;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限码体系，1:1 移植 rainbond-console {@code console/utils/perms.py}
 * （运行版 v6.9.0 与 reference 子模块逐字节一致，见 docs/p1b-7070-reference.md §1）。
 *
 * <p>分段：企业 {@code 1xxxxx} / 团队 {@code 2xxxxx} / 应用 {@code 3xxxxx} / 组件及网关证书等更细分段。
 * 提供：企业角色→权限映射（{@link #ENTERPRISE}/{@link #COMMON_PERMS}）、团队与应用权限树
 * （{@link #team()}/{@link #app()}）、扁平权限装配（{@link #getPerms}）、权限树打包
 * （{@link #packRolePermsTree}，对齐 {@code pack_role_perms_tree}）、企业权限标识展开
 * （{@link #listEnterprisePermsByRoles}）。
 *
 * <p>模板每次按需新建（等价 Python 的 {@code copy.deepcopy}），打包会就地构造新结构、不污染模板。
 */
public final class PermsCatalog {

    private PermsCatalog() {
    }

    /** 单个权限项：名称 + 描述 + 编码（对齐 perms.py 的 {@code [name, desc, code]}）。 */
    public record Perm(String name, String desc, int code) {
    }

    /** 扁平装配后的权限项：组装名（含父分组前缀）+ 编码。 */
    public record AssembledPerm(String name, int code) {
    }

    // ---- 模板构造助手 ----

    private static Perm p(String name, String desc, int code) {
        return new Perm(name, desc, code);
    }

    /** 构造模板节点：交替的 (子分组名, 子节点Map) 键值；"perms" 键固定放叶子列表。 */
    private static Map<String, Object> node(List<Perm> perms, Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("perms", perms);
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static List<Perm> perms(Perm... ps) {
        return List.of(ps);
    }

    // ---- 企业权限映射 ----

    /** 企业角色 → 权限映射（perms.py {@code ENTERPRISE}）。值为该角色的权限项列表。 */
    public static final Map<String, List<Perm>> ENTERPRISE = buildEnterprise();

    /** 通用权限（perms.py {@code common_perms}），任何企业角色都额外叠加（前缀 app_store）。 */
    public static final List<Perm> COMMON_PERMS = List.of(
            p("create_app", "创建应用模板", 110000),
            p("edit_app", "编辑应用模板", 110001),
            p("delete_app", "删除应用模板", 110002),
            p("import_app", "导入应用模板", 110003),
            p("get_app_store", "获取应用商店", 110006),
            p("get_ent_teams", "获取企业的团队列表", 120000));

    private static Map<String, List<Perm>> buildEnterprise() {
        Map<String, List<Perm>> m = new LinkedHashMap<>();
        m.put("admin", List.of(
                p("enterprise_info", "企业视图的功能", 100000),
                p("team_info", "团队相关操作", 100001),
                p("users", "企业用户查询和创建", 100002),
                p("query", "用户模糊查询", 100003),
                p("upload", "上传", 100004)));
        m.put("app_store", List.of(
                p("create_app", "创建应用模板", 110000),
                p("edit_app", "编辑应用模板", 110001),
                p("delete_app", "删除应用模板", 110002),
                p("import_app", "导入应用模板", 110003),
                p("export_app", "导出应用模板", 110004),
                p("create_app_store", "添加应用商店", 110005),
                p("get_app_store", "获取应用商店", 110006),
                p("edit_app_store", "编辑应用商店", 110007),
                p("delete_app_store", "删除应用商店", 110008),
                p("edit_app_version", "编辑应用版本", 110009),
                p("delete_app_version", "删除应用版本", 110010)));
        return m;
    }

    // ---- 应用权限树（perms.py APP）----

    /** 应用权限树模板（每次新建）。 */
    public static Map<String, Object> app() {
        return node(perms(),
                "app_overview", node(perms(
                        p("describe", "查看", 300002), p("edit", "编辑", 300003), p("delete", "删除", 300004),
                        p("start", "启动", 300005), p("stop", "停用", 300006), p("update", "更新", 300007),
                        p("construct", "构建", 300008), p("restart", "重启", 300012), p("create", "组件创建", 300013),
                        p("copy", "快速复制", 300009), p("visit_web_terminal", "组件访问web终端", 300010),
                        p("service_monitor", "组件监控", 300025), p("telescopic", "组件伸缩", 300011),
                        p("env", "组件环境配置", 300016), p("rely", "组件依赖", 300017), p("storage", "组件存储", 300018),
                        p("port", "组件端口", 300019), p("plugin", "组件插件", 300020), p("source", "组件构建源", 300021),
                        p("other_setting", "组件其他设置", 300022))),
                "app_release", node(perms(
                        p("describe", "查看", 310004), p("share", "发布", 310001),
                        p("export", "导出", 310002), p("delete", "删除", 310003))),
                "app_gateway_manage", node(perms(),
                        "app_gateway_monitor", node(perms(p("describe", "查看", 320001))),
                        "app_route_manage", node(perms(
                                p("describe", "查看", 321001), p("create", "创建", 321002),
                                p("edit", "编辑", 321003), p("delete", "删除", 321004))),
                        "app_target_services", node(perms(
                                p("describe", "查看", 322001), p("create", "创建", 322002),
                                p("edit", "编辑", 322003), p("delete", "删除", 322004))),
                        "app_certificate", node(perms(
                                p("describe", "查看", 323001), p("create", "创建", 323002),
                                p("edit", "编辑", 323003), p("delete", "删除", 323004)))),
                "app_upgrade", node(perms(
                        p("app_model_list", "应用模型列表", 330001), p("upgrade_record", "升级记录", 330002),
                        p("upgrade", "升级", 330003), p("rollback", "回滚", 330004))),
                "app_resources", node(perms(
                        p("describe", "查看", 340001), p("create", "创建", 340002),
                        p("edit", "编辑", 340003), p("delete", "删除", 340004))),
                "app_backup", node(perms(
                        p("describe", "查看", 350007), p("add", "新增备份", 350001), p("import", "导入备份", 350002),
                        p("recover", "恢复", 350003), p("move", "迁移", 350004), p("export", "导出", 350005),
                        p("delete", "删除", 350006))),
                "app_config_group", node(perms(
                        p("describe", "查看", 360001), p("create", "创建", 360002),
                        p("edit", "编辑", 360003), p("delete", "删除", 360004))));
    }

    // ---- 团队权限树（perms.py TEAM）----

    /** 团队权限树模板（每次新建）。子分组顺序固定，对齐 7070 的 sub_models 顺序。 */
    public static Map<String, Object> team() {
        return node(perms(),
                "team_overview", node(perms(
                        p("describe", "查看团队信息", 200001), p("app_list", "查看应用信息", 200002))),
                "team_app_create", node(perms(p("describe", "新建应用", 300001))),
                "team_app_manage", node(perms(),
                        "app_overview", node(perms(
                                p("describe", "查看", 300002), p("edit", "编辑", 300003), p("delete", "删除", 300004),
                                p("start", "启动", 300005), p("stop", "停用", 300006), p("update", "更新", 300007),
                                p("construct", "构建", 300008), p("restart", "重启", 300012), p("create", "组件创建", 300013),
                                p("copy", "快速复制", 300009), p("visit_web_terminal", "组件访问web终端", 300010),
                                p("service_monitor", "组件监控", 300025), p("telescopic", "组件伸缩", 300011),
                                p("env", "组件环境配置", 300016), p("rely", "组件依赖", 300017), p("storage", "组件存储", 300018),
                                p("port", "组件端口", 300019), p("plugin", "组件插件", 300020), p("source", "组件构建源", 300021),
                                p("other_setting", "组件其他设置", 300022))),
                        "app_release", node(perms(
                                p("describe", "查看", 310004), p("share", "发布", 310001),
                                p("export", "导出", 310002), p("delete", "删除", 310003))),
                        "app_gateway_manage", node(perms(),
                                "app_gateway_monitor", node(perms(p("describe", "查看", 320001))),
                                "app_route_manage", node(perms(
                                        p("describe", "查看", 321001), p("create", "创建", 321002),
                                        p("edit", "编辑", 321003), p("delete", "删除", 321004))),
                                "app_target_services", node(perms(
                                        p("describe", "查看", 322001), p("create", "创建", 322002),
                                        p("edit", "编辑", 322003), p("delete", "删除", 322004))),
                                "app_certificate", node(perms(
                                        p("describe", "查看", 323001), p("create", "创建", 323002),
                                        p("edit", "编辑", 323003), p("delete", "删除", 323004)))),
                        "app_upgrade", node(perms(
                                p("app_model_list", "应用模型列表", 330001), p("upgrade_record", "升级记录", 330002),
                                p("upgrade", "升级", 330003), p("rollback", "回滚", 330004))),
                        "app_resources", node(perms(
                                p("describe", "查看", 340001), p("create", "创建", 340002),
                                p("edit", "编辑", 340003), p("delete", "删除", 340004))),
                        "app_backup", node(perms(
                                p("describe", "查看", 350007), p("add", "新增备份", 350001), p("import", "导入备份", 350002),
                                p("recover", "恢复", 350003), p("move", "迁移", 350004), p("export", "导出", 350005),
                                p("delete", "删除", 350006))),
                        "app_config_group", node(perms(
                                p("describe", "查看", 360001), p("create", "创建", 360002),
                                p("edit", "编辑", 360003), p("delete", "删除", 360004)))),
                "team_gateway_manage", node(perms(),
                        "team_gateway_monitor", node(perms(p("describe", "查看", 400001))),
                        "team_route_manage", node(perms(
                                p("describe", "查看", 410001), p("create", "创建", 410002),
                                p("edit", "编辑", 410003), p("delete", "删除", 410004))),
                        "team_target_services", node(perms(
                                p("describe", "查看", 420001), p("create", "创建", 420002),
                                p("edit", "编辑", 420003), p("delete", "删除", 420004))),
                        "team_certificate", node(perms(
                                p("describe", "查看", 430001), p("create", "创建", 430002),
                                p("edit", "编辑", 430003), p("delete", "删除", 430004)))),
                "team_plugin_manage", node(perms(
                        p("describe", "查看", 500001), p("create", "创建", 500002),
                        p("edit", "编辑", 500003), p("delete", "删除", 500004))),
                "team_manage", node(perms(),
                        "team_dynamic", node(perms(p("describe", "查看", 600001))),
                        "team_member", node(perms(
                                p("describe", "查看", 610001), p("create", "创建", 610002),
                                p("edit", "编辑", 610003), p("delete", "删除", 610004))),
                        "team_region", node(perms(
                                p("describe", "查看", 620001), p("install", "开通", 620002), p("uninstall", "卸载", 620003))),
                        "team_role", node(perms(
                                p("describe", "查看", 630001), p("create", "创建", 630002),
                                p("edit", "编辑", 630003), p("delete", "删除", 630004))),
                        "team_registry_auth", node(perms(
                                p("describe", "查看", 640001), p("create", "创建", 640002),
                                p("edit", "编辑", 640003), p("delete", "删除", 640004)))));
    }

    // ---- 装配函数 ----

    /**
     * 扁平装配权限项（对齐 {@code get_perms} + {@code assemble_perms}）：递归收集每个叶子，
     * 名称前缀其直接父分组名。用于元数据去重校验。
     */
    @SuppressWarnings("unchecked")
    public static List<AssembledPerm> getPerms(Map<String, Object> kind, String group) {
        List<AssembledPerm> out = new ArrayList<>();
        if (kind == null || kind.isEmpty()) {
            return out;
        }
        List<Perm> ps = (List<Perm>) kind.get("perms");
        if (ps != null) {
            for (Perm perm : ps) {
                out.add(new AssembledPerm(group + "_" + perm.name(), perm.code()));
            }
        }
        for (Map.Entry<String, Object> e : kind.entrySet()) {
            if (e.getKey().equals("perms")) {
                continue;
            }
            out.addAll(getPerms((Map<String, Object>) e.getValue(), e.getKey()));
        }
        return out;
    }

    /** 团队 + 企业全部扁平权限（对齐 {@code check_perms_metadata} 的检查集）。 */
    public static List<AssembledPerm> allTeamAndEnterprisePerms() {
        List<AssembledPerm> out = new ArrayList<>(getPerms(team(), "team"));
        // ENTERPRISE 是 角色→perms 的两层 map，等价 get_perms 对每个角色分组展开
        for (Map.Entry<String, List<Perm>> e : ENTERPRISE.entrySet()) {
            for (Perm perm : e.getValue()) {
                out.add(new AssembledPerm(e.getKey() + "_" + perm.name(), perm.code()));
            }
        }
        return out;
    }

    /**
     * 权限树打包（对齐 {@code pack_role_perms_tree} + {@code __build_perms_list}）：
     * 以模板为骨架，叶子产出 {@code {名称: 布尔}}（不含 code）。{@code isOwner} 时全 true，
     * 否则码命中 {@code trueCodes} 为 true。返回 {@code {kindName: {sub_models:[...], perms:[...]}}}。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> packRolePermsTree(String kindName, Map<String, Object> template,
                                                        Set<Integer> trueCodes, boolean isOwner) {
        Map<String, Object> body = new LinkedHashMap<>();
        List<Object> subModels = new ArrayList<>();
        for (Map.Entry<String, Object> e : template.entrySet()) {
            if (e.getKey().equals("perms")) {
                continue;
            }
            subModels.add(packRolePermsTree(e.getKey(), (Map<String, Object>) e.getValue(), trueCodes, isOwner));
        }
        body.put("sub_models", subModels);
        List<Map<String, Boolean>> leaves = new ArrayList<>();
        for (Perm perm : (List<Perm>) template.get("perms")) {
            Map<String, Boolean> leaf = new LinkedHashMap<>();
            leaf.put(perm.name(), isOwner || trueCodes.contains(perm.code()));
            leaves.add(leaf);
        }
        body.put("perms", leaves);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(kindName, body);
        return out;
    }

    // ---- 企业权限标识展开（perms.py list_enterprise_perms_by_role(s)）----

    private static Set<String> listEnterprisePermsByRole(String role) {
        Set<String> out = new LinkedHashSet<>();
        if ("admin".equals(role)) {
            for (Map.Entry<String, List<Perm>> e : ENTERPRISE.entrySet()) {
                if (e.getKey().equals("admin")) {
                    continue; // 与 rainbond 一致：admin 组本身不计入
                }
                for (Perm perm : e.getValue()) {
                    out.add(e.getKey() + "." + perm.name());
                }
            }
            return out;
        }
        List<Perm> ps = ENTERPRISE.get(role);
        if (ps != null) { // 未知角色按空处理（避开 rainbond 原实现对缺失角色的取值异常）
            for (Perm perm : ps) {
                out.add(role + "." + perm.name());
            }
        }
        return out;
    }

    /**
     * 角色名列表 → 企业权限标识集合（形如 {@code group.name}）。对齐
     * {@code list_enterprise_perms_by_roles}：并各角色展开，并恒定叠加 {@code app_store.<common_perms>}。
     */
    public static Set<String> listEnterprisePermsByRoles(List<String> roles) {
        Set<String> out = new LinkedHashSet<>();
        if (roles != null) {
            for (String role : roles) {
                out.addAll(listEnterprisePermsByRole(role));
            }
        }
        for (Perm perm : COMMON_PERMS) {
            out.add("app_store." + perm.name());
        }
        return out;
    }

    // ---- 整型权限码展开（perms.py list_enterprise_perm_codes_by_role(s) / get_enterprise_adminer_codes）----
    //      鉴权用，区别于上面的字符串 group.name 展开（users/details 展示用），两套不可混用。

    private static Set<Integer> commonPermCodes() {
        Set<Integer> out = new LinkedHashSet<>();
        for (Perm perm : COMMON_PERMS) {
            out.add(perm.code());
        }
        return out;
    }

    /** 团队 kind 全部权限码（owner 短路用，对齐 {@code get_perm_code(TEAM)}）。 */
    public static Set<Integer> allTeamPermCodes() {
        Set<Integer> out = new LinkedHashSet<>();
        for (AssembledPerm p : getPerms(team(), "team")) {
            out.add(p.code());
        }
        return out;
    }

    /** 团队 + 企业全部权限码（admin 全码，对齐 {@code get_enterprise_adminer_codes}）。 */
    public static Set<Integer> getEnterpriseAdminerCodes() {
        Set<Integer> out = new LinkedHashSet<>();
        for (AssembledPerm p : allTeamAndEnterprisePerms()) {
            out.add(p.code());
        }
        return out;
    }

    private static Set<Integer> listEnterprisePermCodesByRole(String role) {
        if ("admin".equals(role)) {
            return getEnterpriseAdminerCodes();
        }
        Set<Integer> out = new LinkedHashSet<>();
        List<Perm> ps = ENTERPRISE.get(role); // 未知角色按空处理（避开 rainbond 原实现的取值异常）
        if (ps != null) {
            for (Perm perm : ps) {
                out.add(perm.code());
            }
        }
        out.addAll(commonPermCodes());
        return out;
    }

    /**
     * 角色名列表 → 整型权限码集合（鉴权用）。对齐 {@code list_enterprise_perm_codes_by_roles}：
     * admin→全码；其它→角色码 + common 码；并恒定叠加 common 码。
     */
    public static Set<Integer> listEnterprisePermCodesByRoles(List<String> roles) {
        Set<Integer> out = new LinkedHashSet<>();
        if (roles != null) {
            for (String role : roles) {
                out.addAll(listEnterprisePermCodesByRole(role));
            }
        }
        out.addAll(commonPermCodes());
        return out;
    }

    // ---- 写支撑：名↔码映射、权限元数据树、权限树降维（perms.py get_perms_name_code_kv / get_structure / unpack）----

    /** 企业模板的节点形态（perms.py {@code ENTERPRISE} 包成 {@code {perms:[], admin:{perms}, app_store:{perms}}}）。 */
    public static Map<String, Object> enterprise() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("perms", List.<Perm>of());
        for (Map.Entry<String, List<Perm>> e : ENTERPRISE.entrySet()) {
            m.put(e.getKey(), node(e.getValue()));
        }
        return m;
    }

    /**
     * {@code 分组名_权限名 → 整型码} 映射（对齐 {@code get_perms_name_code_kv}）。
     * 名称口径同 {@link #getPerms}（直接父分组名前缀），供 {@link #unpackRolePermsTree} 名→码。
     */
    public static Map<String, Integer> getPermsNameCodeKv() {
        Map<String, Integer> kv = new LinkedHashMap<>();
        for (AssembledPerm p : allTeamAndEnterprisePerms()) {
            kv.put(p.name(), p.code());
        }
        return kv;
    }

    /**
     * 权限元数据树（对齐 {@code get_structure}）：{@code {kindName: {sub_models:[...], perms:[{name,desc,code}]}}}。
     * 叶子为 name/desc/code（区别于 packRolePermsTree 的布尔叶子）。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getStructure(String kindName, Map<String, Object> template) {
        Map<String, Object> body = new LinkedHashMap<>();
        List<Object> subModels = new ArrayList<>();
        for (Map.Entry<String, Object> e : template.entrySet()) {
            if (e.getKey().equals("perms")) {
                continue;
            }
            subModels.add(getStructure(e.getKey(), (Map<String, Object>) e.getValue()));
        }
        body.put("sub_models", subModels);
        List<Map<String, Object>> leaves = new ArrayList<>();
        for (Perm perm : (List<Perm>) template.get("perms")) {
            Map<String, Object> leaf = new LinkedHashMap<>();
            leaf.put("name", perm.name());
            leaf.put("desc", perm.desc());
            leaf.put("code", perm.code());
            leaves.add(leaf);
        }
        body.put("perms", leaves);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(kindName, body);
        return out;
    }

    /**
     * 权限元数据完整树（对齐 {@code get_perms_structure}）：{@code {team:{...}, enterprise:{...}}}。
     * {@code team_app_manage} 按团队应用列表重建——kuship 无应用域 → {@code {sub_models:[], perms:[]}}。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getPermsStructure() {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> team = getStructure("team", team());
        // team_app_manage（sub_models[2]）按应用重建；无应用 → 空 {sub_models:[], perms:[]}
        List<Object> subs = (List<Object>) ((Map<String, Object>) team.get("team")).get("sub_models");
        for (Object sub : subs) {
            Map<String, Object> subMap = (Map<String, Object>) sub;
            if (subMap.containsKey("team_app_manage")) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("sub_models", new ArrayList<>());
                empty.put("perms", new ArrayList<>());
                subMap.put("team_app_manage", empty);
                break;
            }
        }
        out.put("team", team.get("team"));
        out.put("enterprise", getStructure("enterprise", enterprise()).get("enterprise"));
        return out;
    }

    /** 降维结果的一项：权限码 + 应用 id（全局为 -1）。 */
    public record RolePermCode(int code, int appId) {
    }

    /**
     * 权限树降维（对齐 {@code unpack_role_perms_tree} / {@code __unpack_to_build_perms_list}）：
     * 遍历提交的 {@code {kind:{sub_models,perms}}} 树，对每个为 {@code true} 的叶子按
     * {@code 节点名_权限名 → 码} 取码；{@code app_<id>} 节点解析 appId，否则继承（默认 -1）。
     * 未知键（catalog 无对应码）跳过。
     */
    public static List<RolePermCode> unpackRolePermsTree(Map<String, Object> permsTree) {
        Map<String, Integer> kv = getPermsNameCodeKv();
        List<RolePermCode> out = new ArrayList<>();
        for (Map.Entry<String, Object> e : permsTree.entrySet()) {
            unpackNode(e.getKey(), e.getValue(), kv, -1, out);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void unpackNode(String kindName, Object nodeObj, Map<String, Integer> kv, int appId,
                                   List<RolePermCode> out) {
        if (!(nodeObj instanceof Map<?, ?> node)) {
            return;
        }
        int currentApp = appId;
        if (kindName.startsWith("app_")) {
            String suffix = kindName.substring("app_".length());
            if (suffix.chars().allMatch(Character::isDigit) && !suffix.isEmpty()) {
                currentApp = Integer.parseInt(suffix);
            }
        }
        Object subModels = node.get("sub_models");
        if (subModels instanceof List<?> subs) {
            for (Object sub : subs) {
                if (sub instanceof Map<?, ?> subMap) {
                    for (Map.Entry<?, ?> se : subMap.entrySet()) {
                        unpackNode((String) se.getKey(), se.getValue(), kv, currentApp, out);
                    }
                }
            }
        }
        Object perms = node.get("perms");
        if (perms instanceof List<?> leaves) {
            for (Object leaf : leaves) {
                if (leaf instanceof Map<?, ?> leafMap) {
                    for (Map.Entry<?, ?> le : leafMap.entrySet()) {
                        if (Boolean.TRUE.equals(le.getValue())) {
                            Integer code = kv.get(kindName + "_" + le.getKey());
                            if (code != null) {
                                out.add(new RolePermCode(code, currentApp));
                            }
                        }
                    }
                }
            }
        }
    }
}
