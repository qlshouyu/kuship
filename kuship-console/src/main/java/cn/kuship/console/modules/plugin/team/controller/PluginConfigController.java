package cn.kuship.console.modules.plugin.team.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.plugin.service.PluginContextLoader;
import cn.kuship.console.modules.plugin.team.entity.PluginConfigGroup;
import cn.kuship.console.modules.plugin.team.entity.PluginConfigItems;
import cn.kuship.console.modules.plugin.team.repository.PluginConfigGroupRepository;
import cn.kuship.console.modules.plugin.team.repository.PluginConfigItemsRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 插件版本下的配置组与配置项：4 endpoint。 */
@RestController
@RequestMapping("/console/teams/{team_name}/plugins/{plugin_id}/version/{build_version}")
public class PluginConfigController {

    private final PluginConfigGroupRepository groupRepo;
    private final PluginConfigItemsRepository itemRepo;
    private final PluginContextLoader loader;

    public PluginConfigController(PluginConfigGroupRepository groupRepo,
                                     PluginConfigItemsRepository itemRepo,
                                     PluginContextLoader loader) {
        this.groupRepo = groupRepo;
        this.itemRepo = itemRepo;
        this.loader = loader;
    }

    @GetMapping(value = {"/config", "/config/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    public ApiResult get(@PathVariable("team_name") String teamName,
                            @PathVariable("plugin_id") String pluginId,
                            @PathVariable("build_version") String buildVersion) {
        loader.requirePlugin(teamName, pluginId);
        List<PluginConfigGroup> groups = groupRepo.findByPluginIdAndBuildVersion(pluginId, buildVersion);
        List<PluginConfigItems> items = itemRepo.findByPluginIdAndBuildVersion(pluginId, buildVersion);
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("config_groups", groups.stream().map(PluginConfigController::groupToBean).toList());
        bean.put("config_items", items.stream().map(PluginConfigController::itemToBean).toList());
        return GeneralMessage.ok(bean);
    }

    @PutMapping(value = {"/config", "/config/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    @Transactional
    @SuppressWarnings("unchecked")
    public ApiResult put(@PathVariable("team_name") String teamName,
                            @PathVariable("plugin_id") String pluginId,
                            @PathVariable("build_version") String buildVersion,
                            @RequestBody Map<String, Object> body) {
        loader.requirePlugin(teamName, pluginId);
        // 全量替换
        groupRepo.deleteByPluginIdAndBuildVersion(pluginId, buildVersion);
        itemRepo.deleteByPluginIdAndBuildVersion(pluginId, buildVersion);

        Object groupsObj = body.get("config_groups");
        if (groupsObj instanceof List<?> groups) {
            for (Object o : groups) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> g = (Map<String, Object>) o;
                PluginConfigGroup grp = new PluginConfigGroup();
                grp.setPluginId(pluginId);
                grp.setBuildVersion(buildVersion);
                grp.setConfigName(String.valueOf(g.getOrDefault("config_name", "default")));
                grp.setServiceMetaType(String.valueOf(g.getOrDefault("service_meta_type", "upstream_port")));
                grp.setInjection(String.valueOf(g.getOrDefault("injection", "auto")));
                grp.setCreateTime(LocalDateTime.now());
                groupRepo.save(grp);

                Object itemsObj = g.get("items");
                if (itemsObj instanceof List<?> items) {
                    for (Object io : items) {
                        if (!(io instanceof Map)) continue;
                        Map<String, Object> it = (Map<String, Object>) io;
                        PluginConfigItems item = new PluginConfigItems();
                        item.setPluginId(pluginId);
                        item.setBuildVersion(buildVersion);
                        item.setServiceMetaType(grp.getServiceMetaType());
                        item.setAttrName(requireString(it, "attr_name"));
                        item.setAttrType(String.valueOf(it.getOrDefault("attr_type", "string")));
                        item.setAttrAltValue(String.valueOf(it.getOrDefault("attr_alt_value", "")));
                        item.setAttrDefaultValue((String) it.get("attr_default_value"));
                        item.setIsChange(Boolean.TRUE.equals(it.get("is_change")));
                        item.setAttrInfo((String) it.get("attr_info"));
                        item.setProtocol((String) it.get("protocol"));
                        item.setCreateTime(LocalDateTime.now());
                        itemRepo.save(item);
                    }
                }
            }
        }
        return GeneralMessage.ok();
    }

    @DeleteMapping(value = {"/config", "/config/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    @Transactional
    public ApiResult delete(@PathVariable("plugin_id") String pluginId,
                              @PathVariable("build_version") String buildVersion) {
        groupRepo.deleteByPluginIdAndBuildVersion(pluginId, buildVersion);
        itemRepo.deleteByPluginIdAndBuildVersion(pluginId, buildVersion);
        return GeneralMessage.ok();
    }

    @GetMapping(value = {"/preview", "/preview/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    public ApiResult preview(@PathVariable("plugin_id") String pluginId,
                                @PathVariable("build_version") String buildVersion) {
        List<PluginConfigItems> items = itemRepo.findByPluginIdAndBuildVersion(pluginId, buildVersion);
        return GeneralMessage.ok(Map.of(
                "preview", items.stream().map(PluginConfigController::itemToBean).toList(),
                "build_version", buildVersion));
    }

    private static String requireString(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (!(v instanceof String s) || s.isBlank()) {
            throw new ServiceHandleException(400, "missing " + key, "缺少 " + key);
        }
        return s;
    }

    static Map<String, Object> groupToBean(PluginConfigGroup g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("config_name", g.getConfigName());
        m.put("service_meta_type", g.getServiceMetaType());
        m.put("injection", g.getInjection());
        return m;
    }

    static Map<String, Object> itemToBean(PluginConfigItems i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("attr_name", i.getAttrName());
        m.put("attr_type", i.getAttrType());
        m.put("attr_alt_value", i.getAttrAltValue());
        m.put("attr_default_value", i.getAttrDefaultValue());
        m.put("is_change", i.getIsChange());
        m.put("attr_info", i.getAttrInfo());
        m.put("protocol", i.getProtocol());
        return m;
    }
}
