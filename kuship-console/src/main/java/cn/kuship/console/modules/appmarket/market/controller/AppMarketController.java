package cn.kuship.console.modules.appmarket.market.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.appmarket.market.entity.AppMarket;
import cn.kuship.console.modules.appmarket.market.repository.AppMarketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 远程应用市场凭据 + 远程模板浏览。 */
@RestController
@RequestMapping("/console/enterprise/{enterprise_id}/cloud")
public class AppMarketController {

    private static final Logger log = LoggerFactory.getLogger(AppMarketController.class);

    private final AppMarketRepository repo;
    private final RestClient remoteMarket;

    public AppMarketController(AppMarketRepository repo) {
        this.repo = repo;
        // 远程市场 OpenAPI 不依赖固定 baseUrl —— 每次调用按 market.url 动态拼，URL 直接传 absolute
        this.remoteMarket = RestClient.builder().build();
    }

    /**
     * UI {@code CreateComponentModal::fetchMarketStores} 用 {@code list.filter(item => item.status === 1)}
     * 决定是否显示市场入口；UI {@code Explore} 等多处也按 rainbond 契约消费 {@code alias} / {@code access_actions}。
     * rainbond 端这些扩展字段来自运行时探测远程市场（{@code extend=true}），kuship 暂不做远程探测，
     * 用 hardcode + 派生默认值占位（{@code status=1} / 按 type 派生 alias），保留远程接通后替换为真值的空间。
     */
    @GetMapping(value = {"/markets", "/markets/"})
    public ApiResult list(@PathVariable("enterprise_id") String enterpriseId) {
        List<AppMarket> all = repo.findByEnterpriseId(enterpriseId);
        return GeneralMessage.okList(all.stream().map(AppMarketController::toBean).toList());
    }

    @PostMapping(value = {"/markets", "/markets/"})
    @Transactional
    public ApiResult create(@PathVariable("enterprise_id") String enterpriseId,
                              @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        if (name == null) throw new ServiceHandleException(400, "missing name", "缺少 name");
        if (repo.findByEnterpriseIdAndName(enterpriseId, name).isPresent()) {
            throw new ServiceHandleException(400, "market exists", "市场名已存在");
        }
        AppMarket m = new AppMarket();
        m.setName(name);
        m.setUrl((String) body.getOrDefault("url", ""));
        m.setDomain((String) body.getOrDefault("domain", ""));
        m.setAccessKey((String) body.get("access_key"));
        m.setEnterpriseId(enterpriseId);
        m.setType((String) body.getOrDefault("type", "rainstore"));
        m.setIsPersonal(false);
        repo.save(m);
        return GeneralMessage.ok(toBean(m));
    }

    @GetMapping(value = {"/markets/{market_name}", "/markets/{market_name}/"})
    public ApiResult detail(@PathVariable("enterprise_id") String enterpriseId,
                              @PathVariable("market_name") String marketName) {
        AppMarket m = repo.findByEnterpriseIdAndName(enterpriseId, marketName)
                .orElseThrow(() -> new ServiceHandleException(404, "market not found", "市场不存在"));
        return GeneralMessage.ok(toBean(m));
    }

    @PutMapping(value = {"/markets/{market_name}", "/markets/{market_name}/"})
    @Transactional
    public ApiResult update(@PathVariable("enterprise_id") String enterpriseId,
                              @PathVariable("market_name") String marketName,
                              @RequestBody Map<String, Object> body) {
        AppMarket m = repo.findByEnterpriseIdAndName(enterpriseId, marketName)
                .orElseThrow(() -> new ServiceHandleException(404, "market not found", "市场不存在"));
        if (body.get("url") instanceof String u) m.setUrl(u);
        if (body.get("domain") instanceof String d) m.setDomain(d);
        if (body.get("access_key") instanceof String k) m.setAccessKey(k);
        repo.save(m);
        return GeneralMessage.ok(toBean(m));
    }

    @DeleteMapping(value = {"/markets/{market_name}", "/markets/{market_name}/"})
    @Transactional
    public ApiResult delete(@PathVariable("enterprise_id") String enterpriseId,
                              @PathVariable("market_name") String marketName) {
        repo.deleteByEnterpriseIdAndName(enterpriseId, marketName);
        return GeneralMessage.ok();
    }

    @PostMapping(value = {"/bind-markets", "/bind-markets/"})
    @Transactional
    @SuppressWarnings("unchecked")
    public ApiResult bindBatch(@PathVariable("enterprise_id") String enterpriseId,
                                  @RequestBody Map<String, Object> body) {
        Object items = body.get("markets");
        int count = 0;
        if (items instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> mm = (Map<String, Object>) o;
                String name = String.valueOf(mm.get("name"));
                if (repo.findByEnterpriseIdAndName(enterpriseId, name).isPresent()) continue;
                AppMarket am = new AppMarket();
                am.setName(name);
                am.setUrl(String.valueOf(mm.getOrDefault("url", "")));
                am.setDomain(String.valueOf(mm.getOrDefault("domain", "")));
                am.setAccessKey(String.valueOf(mm.getOrDefault("access_key", "")));
                am.setEnterpriseId(enterpriseId);
                am.setType(String.valueOf(mm.getOrDefault("type", "rainstore")));
                am.setIsPersonal(false);
                repo.save(am);
                count++;
            }
        }
        return GeneralMessage.ok(Map.of("bound", count));
    }

    @GetMapping(value = {"/bindable-markets", "/bindable-markets/"})
    public ApiResult bindable(@PathVariable("enterprise_id") String enterpriseId) {
        // 简化：返回固定的内置市场列表（rainbond 默认就是这样的占位）
        return GeneralMessage.okList(List.of(
                Map.of("name", "rainstore", "domain", "store.rainbond.com", "type", "rainstore"),
                Map.of("name", "local", "domain", "local", "type", "local")));
    }

    /**
     * 远程市场应用列表：调 {@code ${market.url}/app-server/openapi/apps}（access_key 作 Authorization header），
     * 把响应字段映射到 rainbond {@code app_models_serializers} 输出契约（snake_case 字段 + market 元数据）。
     *
     * <p>分页 {@code page/page_size} 透传；{@code query/arch/query_all} 透传给上游。
     * 远程不可达 / 鉴权失败时返空 list，与 rainbond 端 {@code apiException} 装饰器吞掉异常的实际行为对齐
     * （前端走 marketAvailable=false 兜底）。
     */
    @GetMapping(value = {"/markets/{market_name}/app-models", "/markets/{market_name}/app-models/"})
    public ApiResult listRemoteAppModels(@PathVariable("enterprise_id") String enterpriseId,
                                          @PathVariable("market_name") String marketName,
                                          @RequestParam(name = "page", defaultValue = "1") int page,
                                          @RequestParam(name = "page_size", defaultValue = "10") int pageSize,
                                          @RequestParam(name = "query", required = false) String query,
                                          @RequestParam(name = "arch", required = false) String arch,
                                          @RequestParam(name = "query_all", required = false) Boolean queryAll) {
        AppMarket market = repo.findByEnterpriseIdAndName(enterpriseId, marketName)
                .orElseThrow(() -> new ServiceHandleException(404, "market not found", "市场不存在"));

        StringBuilder url = new StringBuilder(market.getUrl().replaceAll("/+$", ""))
                .append("/app-server/openapi/apps")
                .append("?marketDomain=").append(market.getDomain())
                .append("&page=").append(page)
                .append("&pageSize=").append(pageSize);
        if (query != null && !query.isBlank()) url.append("&query=").append(query);
        if (arch != null && !arch.isBlank()) url.append("&arch=").append(arch);
        if (Boolean.TRUE.equals(queryAll)) url.append("&queryAll=true");

        Map<String, Object> upstream;
        try {
            upstream = remoteMarket.get()
                    .uri(url.toString())
                    .header("Authorization", market.getAccessKey() != null ? market.getAccessKey() : "")
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException e) {
            log.warn("[market-apps] upstream {} returned {}: {}", url, e.getStatusCode(), e.getResponseBodyAsString());
            return remoteFailureBean(market, page, pageSize);
        } catch (RuntimeException e) {
            log.warn("[market-apps] upstream {} unreachable: {}", url, e.toString());
            return remoteFailureBean(market, page, pageSize);
        }

        List<Map<String, Object>> mapped = mapApps(market, upstream);
        Object total = upstream != null && upstream.get("total") != null ? upstream.get("total") : mapped.size();

        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("page", page);
        extras.put("page_size", pageSize);
        extras.put("total", total);
        return GeneralMessage.okWithExtras(Map.of(), mapped, extras);
    }

    private static ApiResult remoteFailureBean(AppMarket market, int page, int pageSize) {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("page", page);
        extras.put("page_size", pageSize);
        extras.put("total", 0);
        return GeneralMessage.okWithExtras(Map.of(), List.of(), extras);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapApps(AppMarket market, Map<String, Object> upstream) {
        if (upstream == null) return List.of();
        Object apps = upstream.get("apps");
        if (!(apps instanceof List<?> rawList)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>(rawList.size());
        for (Object o : rawList) {
            if (!(o instanceof Map<?, ?> rawApp)) continue;
            Map<String, Object> a = (Map<String, Object>) rawApp;

            // 版本数组：upstream apps[].versions 是 camelCase 字段，需映射为 rainbond serializer 的 snake_case
            // UI CreateAppFromMarketForm 直接从 props.showCreate.versions 取，必须含 app_version / cpu / memory
            List<Map<String, Object>> versions = mapVersions(a.get("versions"));
            // arch 数组：rainbond 端是 versions[].arch 的去重集合，arch 为空时 fallback "amd64"
            java.util.Set<String> archSet = new java.util.LinkedHashSet<>();
            for (Map<String, Object> v : versions) {
                Object arch = v.get("arch");
                archSet.add(arch instanceof String s && !s.isBlank() ? s : "amd64");
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("app_id", a.get("appKeyID"));
            m.put("app_name", a.get("name"));
            m.put("update_time", a.get("updateTime"));
            m.put("local_market_id", market.getId());
            m.put("local_market_name", market.getName());
            m.put("enterprise_id", market.getEnterpriseId());
            m.put("source", "market");
            m.put("versions", versions);
            m.put("arch", new ArrayList<>(archSet));
            m.put("tags", a.getOrDefault("tags", List.of()));
            m.put("logo", a.get("logo"));
            m.put("market_id", a.get("marketID"));
            m.put("market_name", a.get("marketName"));
            m.put("market_url", a.get("marketURL"));
            m.put("install_number", a.getOrDefault("installCount", 0));
            m.put("describe", a.get("desc"));
            m.put("dev_status", a.get("devStatus"));
            m.put("app_detail_url", a.get("appDetailURL"));
            m.put("create_time", a.get("createTime"));
            m.put("download_number", a.getOrDefault("downloadCount", 0));
            m.put("details", a.get("introduction"));
            m.put("details_html", a.get("introductionHtml"));
            m.put("is_official", a.getOrDefault("isOfficial", false));
            m.put("publish_type", a.get("publishType"));
            m.put("start_count", a.getOrDefault("startCount", 0));
            out.add(m);
        }
        return out;
    }

    @GetMapping(value = {"/markets/{market_name}/app-models/{model_id}/versions",
                          "/markets/{market_name}/app-models/{model_id}/versions/"})
    public ApiResult listRemoteVersions(@PathVariable("market_name") String marketName,
                                            @PathVariable("model_id") String modelId) {
        return GeneralMessage.okList(List.of());
    }

    @GetMapping(value = {"/markets/{market_name}/app-models/{model_id}/version/{version}",
                          "/markets/{market_name}/app-models/{model_id}/version/{version}/"})
    public ApiResult remoteVersionDetail(@PathVariable("market_name") String marketName,
                                            @PathVariable("model_id") String modelId,
                                            @PathVariable("version") String version) {
        return GeneralMessage.ok(Map.of("model_id", modelId, "version", version));
    }

    static Map<String, Object> toBean(AppMarket m) {
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("ID", m.getId());
        bean.put("name", m.getName());
        bean.put("url", m.getUrl());
        bean.put("domain", m.getDomain());
        bean.put("type", m.getType());
        bean.put("enterprise_id", m.getEnterpriseId());
        bean.put("access_key", m.getAccessKey());
        bean.put("alias", deriveAlias(m));
        bean.put("description", deriveDescription(m));
        // 占位：运行时未做远程市场探测，默认认为可达；远程探测落地后改为按调用结果设置。
        bean.put("status", 1);
        bean.put("access_actions", List.of("ReadInstall", "OnlyRead"));
        bean.put("version", "");
        return bean;
    }

    private static String deriveAlias(AppMarket m) {
        if ("rainstore".equalsIgnoreCase(m.getType()) && "RainbondMarket".equals(m.getName())) {
            return "开源应用市场";
        }
        return m.getName();
    }

    private static String deriveDescription(AppMarket m) {
        if ("rainstore".equalsIgnoreCase(m.getType()) && "RainbondMarket".equals(m.getName())) {
            return "Rainbond 社区开源商店主要为 Rainbond 开源用户提供应用分发和交付服务。";
        }
        return "";
    }

    /**
     * 映射 upstream {@code apps[].versions[]}（camelCase）到 rainbond {@code app_models_serializers}
     * 输出的 snake_case 形状。UI {@code CreateAppFromMarketForm} 安装弹框依赖 {@code app_version}（下拉选项）+
     * {@code cpu/memory}（资源占用）+ {@code app_key_id}（安装时定位 model）。
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapVersions(Object raw) {
        if (!(raw instanceof List<?> rawVersions)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>(rawVersions.size());
        for (Object o : rawVersions) {
            if (!(o instanceof Map<?, ?> rv)) continue;
            Map<String, Object> v = (Map<String, Object>) rv;
            String arch = v.get("arch") instanceof String s && !s.isBlank() ? s : "amd64";
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("arch", arch);
            m.put("is_plugin", v.getOrDefault("is_plugin", false));
            m.put("app_key_id", v.get("appKeyID"));
            m.put("app_version", v.get("appVersion"));
            m.put("app_version_alias", v.getOrDefault("appVersionAlias", ""));
            m.put("create_time", v.get("createTime"));
            m.put("desc", v.get("desc"));
            // rainbond 端把 desc 同时复制为 rainbond_version（历史字段，UI 某些页面会读）
            m.put("rainbond_version", v.get("desc"));
            m.put("update_time", v.get("updateTime"));
            m.put("update_version", v.getOrDefault("updateVersion", 0));
            m.put("cpu", v.getOrDefault("cpu", 0));
            m.put("memory", v.getOrDefault("memory", 0));
            out.add(m);
        }
        return out;
    }
}
