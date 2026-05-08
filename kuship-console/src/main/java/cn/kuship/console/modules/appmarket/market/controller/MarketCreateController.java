package cn.kuship.console.modules.appmarket.market.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.appmarket.market.entity.AppMarket;
import cn.kuship.console.modules.appmarket.market.entity.RainbondCenterAppVersion;
import cn.kuship.console.modules.appmarket.market.repository.AppMarketRepository;
import cn.kuship.console.modules.appmarket.market.repository.RainbondCenterAppVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 从模板/命令创建组件入口（{@code market_create} + {@code cmd_create}）。
 *
 * <p>{@code market_create} 是云市场 / 本地模板安装的入口。MVP 实现仅做最小校验：
 * <ol>
 *   <li>{@code install_from_cloud=true}：调远程市场拿 app_template，与本机/集群架构比对，
 *       不匹配返 404 + {@code "应用架构与构建节点架构不匹配"}（rainbond 行为对齐）；匹配则返 501（暂未实现真实创建链路）。</li>
 *   <li>{@code install_from_cloud=false}：从本地 {@code rainbond_center_app_version} 表查模板。</li>
 * </ol>
 *
 * <p>架构来源（standalone 部署简化）：用 JVM 跑在的宿主架构 {@code os.arch} 推断节点架构 —
 * standalone 单容器内 k3s 节点 = kuship-console 进程同宿主 = 同架构。RKE2 多节点部署需后续切换到 region API
 * {@code GET /v2/cluster/nodes/arch}（独立 hardening change）。
 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps")
public class MarketCreateController {

    private static final Logger log = LoggerFactory.getLogger(MarketCreateController.class);

    private final RainbondCenterAppVersionRepository versionRepo;
    private final AppMarketRepository marketRepo;
    private final RestClient remoteMarket;

    public MarketCreateController(RainbondCenterAppVersionRepository versionRepo,
                                   AppMarketRepository marketRepo) {
        this.versionRepo = versionRepo;
        this.marketRepo = marketRepo;
        this.remoteMarket = RestClient.builder().build();
    }

    @PostMapping(value = {"/market_create", "/market_create/"})
    public ApiResult marketCreate(@PathVariable("team_name") String teamName,
                                   @RequestBody Map<String, Object> body) {
        log.info("[market_create] team={} body={}", teamName, body);
        String appId = stringOrNull(body.get("app_id"));
        // UI 历史发的是 app_version，少数路径还会发 version；都接受
        String version = stringOrNull(body.getOrDefault("app_version", body.get("version")));
        if (appId == null || version == null) {
            throw new ServiceHandleException(400, "missing app_id/version", "缺少 app_id 或 version");
        }
        boolean installFromCloud = Boolean.TRUE.equals(body.get("install_from_cloud"));

        if (installFromCloud) {
            String marketName = stringOrNull(body.get("market_name"));
            String enterpriseId = stringOrNull(body.get("enterprise_id"));
            checkCloudArchOrThrow(enterpriseId, marketName, appId, version);
            // 架构匹配通过 — 但完整 install_app 链路（拉模板 / 创建 service_group / 调 region create_service /
            // 入库 tenant_service 等）尚未迁移。明确返回 501，避免悄悄成功误导用户。
            throw new ServiceHandleException(501, "cloud install not implemented",
                    "云端应用安装功能尚在迁移中，请稍后重试");
        }

        RainbondCenterAppVersion v = versionRepo.findByAppIdAndVersion(appId, version)
                .orElseThrow(() -> new ServiceHandleException(404, "version not found", "应用模板版本不存在"));
        // 与 rainbond market_app_service.install_app:96-97 对齐：本地模板安装也要校验架构
        enforceArchMatchOrThrow(v.getArch());
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
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("kind", kind);
        bean.put("command", body.get("command"));
        bean.put("group_id", body.get("group_id"));
        return GeneralMessage.ok(bean);
    }

    /**
     * 复刻 rainbond {@code services/market_app_service.py:96-97} 的架构匹配检查。
     * 不匹配抛 404 + "应用架构与构建节点架构不匹配"；远程不可达视为可装（与 rainbond 行为一致：
     * region API 失败时不阻挡安装，避免外部依赖故障级联）。
     */
    private void checkCloudArchOrThrow(String enterpriseId, String marketName, String appKeyId, String appVersion) {
        if (marketName == null) return;
        Optional<AppMarket> opt = enterpriseId != null
                ? marketRepo.findByEnterpriseIdAndName(enterpriseId, marketName)
                : marketRepo.findByEnterpriseId("").stream().filter(m -> marketName.equals(m.getName())).findFirst();
        if (opt.isEmpty()) {
            // 兜底：扫所有 enterprise 的同名市场
            opt = marketRepo.findAll().stream().filter(m -> marketName.equals(m.getName())).findFirst();
        }
        if (opt.isEmpty()) return;
        AppMarket market = opt.get();

        String templateArch = fetchTemplateArch(market, appKeyId, appVersion);
        enforceArchMatchOrThrow(templateArch);
    }

    /**
     * 模板架构与节点架构对比 —— 与 rainbond {@code market_app_service.install_app:96-97} 行为一致：
     * template_arch 不在节点架构集合里，且节点架构数 &lt; 2 时报 404；节点架构数 &ge; 2（多架构集群）跳过严检。
     */
    private void enforceArchMatchOrThrow(String templateArch) {
        if (templateArch == null || templateArch.isBlank()) templateArch = "amd64";
        Set<String> nodeArches = currentNodeArches();
        if (!nodeArches.contains(templateArch) && nodeArches.size() < 2) {
            log.warn("[market_create] arch mismatch: template={} nodes={}", templateArch, nodeArches);
            throw new ServiceHandleException(404,
                    "app arch does not match build node arch",
                    "应用架构与构建节点架构不匹配");
        }
    }

    @SuppressWarnings("unchecked")
    private String fetchTemplateArch(AppMarket market, String appKeyId, String appVersion) {
        String url = market.getUrl().replaceAll("/+$", "")
                + "/app-server/openapi/apps?marketDomain=" + market.getDomain()
                + "&query=" + appKeyId + "&page=1&pageSize=10";
        Map<String, Object> resp;
        try {
            resp = remoteMarket.get().uri(url)
                    .header("Authorization", market.getAccessKey() != null ? market.getAccessKey() : "")
                    .retrieve().body(Map.class);
        } catch (RestClientException e) {
            log.warn("[market_create] fetch template arch failed: {}", e.toString());
            return null;
        }
        if (resp == null) return null;
        Object apps = resp.get("apps");
        if (!(apps instanceof List<?> list)) return null;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> mApp)) continue;
            if (!appKeyId.equals(mApp.get("appKeyID"))) continue;
            Object versions = ((Map<String, Object>) mApp).get("versions");
            if (!(versions instanceof List<?> vlist)) continue;
            for (Object vo : vlist) {
                if (!(vo instanceof Map<?, ?> mv)) continue;
                if (appVersion.equals(mv.get("appVersion"))) {
                    Object arch = mv.get("arch");
                    if (arch instanceof String s && !s.isBlank()) return s;
                    return "amd64";
                }
            }
        }
        return null;
    }

    /**
     * standalone 简化：直接用 JVM 宿主架构代表节点架构。
     * RKE2 多节点 / 跨架构集群部署需切换到 region API {@code GET /v2/cluster/nodes/arch}
     * （已有 ClusterOperations 接口骨架，待 follow-up 接通）。
     */
    private static Set<String> currentNodeArches() {
        String osArch = System.getProperty("os.arch", "amd64").toLowerCase();
        String normalized = switch (osArch) {
            case "x86_64", "amd64" -> "amd64";
            case "aarch64", "arm64" -> "arm64";
            default -> osArch;
        };
        return Set.of(normalized);
    }

    private static String stringOrNull(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
