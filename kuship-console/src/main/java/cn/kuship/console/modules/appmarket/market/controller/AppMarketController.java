package cn.kuship.console.modules.appmarket.market.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.appmarket.market.entity.AppMarket;
import cn.kuship.console.modules.appmarket.market.repository.AppMarketRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 远程应用市场凭据 + 远程模板浏览。 */
@RestController
@RequestMapping("/console/enterprise/{enterprise_id}/cloud")
public class AppMarketController {

    private final AppMarketRepository repo;

    public AppMarketController(AppMarketRepository repo) {
        this.repo = repo;
    }

    @GetMapping(value = {"/markets", "/markets/"})
    public ApiResult list(@PathVariable("enterprise_id") String enterpriseId) {
        List<AppMarket> all = repo.findByEnterpriseId(enterpriseId);
        return GeneralMessage.okList(all.stream().map(AppMarketController::toMaskedBean).toList());
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
        return GeneralMessage.ok(toMaskedBean(m));
    }

    @GetMapping(value = {"/markets/{market_name}", "/markets/{market_name}/"})
    public ApiResult detail(@PathVariable("enterprise_id") String enterpriseId,
                              @PathVariable("market_name") String marketName) {
        AppMarket m = repo.findByEnterpriseIdAndName(enterpriseId, marketName)
                .orElseThrow(() -> new ServiceHandleException(404, "market not found", "市场不存在"));
        return GeneralMessage.ok(toMaskedBean(m));
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
        return GeneralMessage.ok(toMaskedBean(m));
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

    @GetMapping(value = {"/markets/{market_name}/app-models", "/markets/{market_name}/app-models/"})
    public ApiResult listRemoteAppModels(@PathVariable("enterprise_id") String enterpriseId,
                                            @PathVariable("market_name") String marketName) {
        // MVP：空数据透传；远程拉取留作 hardening
        return GeneralMessage.ok(Map.of("list", List.of(), "total", 0));
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

    static Map<String, Object> toMaskedBean(AppMarket m) {
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("name", m.getName());
        bean.put("url", m.getUrl());
        bean.put("domain", m.getDomain());
        bean.put("type", m.getType());
        String mask = m.getAccessKey() == null ? "" : (m.getAccessKey().length() <= 4 ? "***"
                : "***" + m.getAccessKey().substring(m.getAccessKey().length() - 4));
        bean.put("access_key_masked", mask);
        return bean;
    }
}
