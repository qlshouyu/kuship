package cn.kuship.console.modules.appmarket.market.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.appmarket.market.entity.RainbondCenterAppVersion;
import cn.kuship.console.modules.appmarket.market.repository.RainbondCenterAppVersionRepository;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** 从模板/命令创建组件入口（market_create + cmd_create）。 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps")
public class MarketCreateController {

    private final RainbondCenterAppVersionRepository versionRepo;

    public MarketCreateController(RainbondCenterAppVersionRepository versionRepo) {
        this.versionRepo = versionRepo;
    }

    @PostMapping(value = {"/market_create", "/market_create/"})
    public ApiResult marketCreate(@PathVariable("team_name") String teamName,
                                     @RequestBody Map<String, Object> body) {
        String appId = (String) body.get("app_id");
        String version = (String) body.get("version");
        if (appId == null || version == null) {
            throw new ServiceHandleException(400, "missing app_id/version", "缺少 app_id 或 version");
        }
        RainbondCenterAppVersion v = versionRepo.findByAppIdAndVersion(appId, version)
                .orElseThrow(() -> new ServiceHandleException(404, "version not found", "应用模板版本不存在"));
        // MVP：仅返回模板内容，由前端解析 + 用户逐组件创建（避免大事务一次性创建多组件失败混乱）
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("app_id", appId);
        bean.put("version", version);
        bean.put("template", v.getAppTemplate());
        bean.put("group_id", body.get("group_id"));
        bean.put("region_name", body.get("region_name"));
        return GeneralMessage.ok(bean);
    }

    @PostMapping(value = {"/cmd_create", "/cmd_create/"})
    public ApiResult cmdCreate(@PathVariable("team_name") String teamName,
                                  @RequestBody Map<String, Object> body) {
        String kind = String.valueOf(body.getOrDefault("kind", "image"));
        // MVP：透传命令信息给前端做后续 form 创建（rainbond 原版本就是 5-step 引导）
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("kind", kind);
        bean.put("command", body.get("command"));
        bean.put("group_id", body.get("group_id"));
        return GeneralMessage.ok(bean);
    }
}
