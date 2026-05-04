package cn.kuship.console.modules.plugin.market.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.plugin.market.entity.RainbondCenterPlugin;
import cn.kuship.console.modules.plugin.market.repository.RainbondCenterPluginRepository;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 应用市场插件 + 内置插件列表 + 一键安装：8 endpoint。 */
@RestController
public class MarketPluginController {

    private final RainbondCenterPluginRepository repo;

    public MarketPluginController(RainbondCenterPluginRepository repo) {
        this.repo = repo;
    }

    @GetMapping(value = {"/console/market/plugins", "/console/market/plugins/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    public ApiResult listMarket() {
        return GeneralMessage.okList(repo.findAll().stream().map(MarketPluginController::toBean).toList());
    }

    @PostMapping(value = {"/console/market/plugins/sync", "/console/market/plugins/sync/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    public ApiResult sync(@RequestBody(required = false) Map<String, Object> body) {
        // MVP：占位（远程市场拉取留作 hardening）
        return GeneralMessage.ok(Map.of("synced", 0));
    }

    @PostMapping(value = {"/console/market/plugins/sync-template", "/console/market/plugins/sync-template/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    public ApiResult syncTemplate(@RequestBody(required = false) Map<String, Object> body) {
        return GeneralMessage.ok(Map.of("synced_templates", 0));
    }

    @PostMapping(value = {"/console/market/plugins/uninstall-template", "/console/market/plugins/uninstall-template/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    public ApiResult uninstallTemplate(@RequestBody(required = false) Map<String, Object> body) {
        return GeneralMessage.ok();
    }

    @PostMapping(value = {"/console/market/plugins/install", "/console/market/plugins/install/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    public ApiResult install(@RequestBody Map<String, Object> body) {
        // MVP：仅返回安装事件占位（深度安装需复用 team controller create + config 逻辑，hardening）
        return GeneralMessage.ok(Map.of("plugin_key", body.get("plugin_key"), "installed", true));
    }

    @GetMapping(value = {"/console/plugins", "/console/plugins/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    public ApiResult listInternal() {
        return GeneralMessage.okList(repo.findAll().stream().map(MarketPluginController::toBean).toList());
    }

    @GetMapping(value = {"/console/plugins/installable", "/console/plugins/installable/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    public ApiResult listInstallable() {
        // MVP：返回所有市场插件作为"可安装"
        return GeneralMessage.okList(repo.findAll().stream().map(MarketPluginController::toBean).toList());
    }

    @GetMapping(value = {"/console/teams/{tenantName}/apps/plugins", "/console/teams/{tenantName}/apps/plugins/"})
    @RequirePerm(PermCode.TEAM_PLUGIN_MANAGE)
    public ApiResult listTeamMarketPlugins(@PathVariable("tenantName") String tenantName) {
        return GeneralMessage.okList(repo.findAll().stream().map(MarketPluginController::toBean).toList());
    }

    static Map<String, Object> toBean(RainbondCenterPlugin p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plugin_key", p.getPluginKey());
        m.put("plugin_name", p.getPluginName());
        m.put("plugin_id", p.getPluginId());
        m.put("category", p.getCategory());
        m.put("version", p.getVersion());
        m.put("build_version", p.getBuildVersion());
        m.put("pic", p.getPic());
        m.put("scope", p.getScope());
        m.put("source", p.getSource());
        m.put("desc", p.getDescribe());
        m.put("is_complete", p.getIsComplete());
        m.put("update_time", p.getUpdateTime());
        return m;
    }
}
