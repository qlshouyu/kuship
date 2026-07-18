package cn.kuship.console.modules.region.controller;

import cn.kuship.console.common.response.SkipResponseWrapper;
import cn.kuship.console.modules.region.service.MonitorQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prometheus 即时查询代理（对齐 rainbond MonitorQueryView）。
 * 返回 Prometheus 原生格式（裸 JSON，不走 general_message 信封），故标注 {@link SkipResponseWrapper}。
 */
@RestController
public class MonitorQueryController {

    private final MonitorQueryService service;

    public MonitorQueryController(MonitorQueryService service) {
        this.service = service;
    }

    /** GET /console/monitor/query?query=...&region_name=... —— 原文透传（String），produces 显式 json。 */
    @GetMapping(value = "/console/monitor/query", produces = "application/json")
    @SkipResponseWrapper
    public String query(@RequestParam(value = "query", required = false, defaultValue = "") String query,
                        @RequestParam(value = "region_name", required = false, defaultValue = "") String regionName) {
        return service.query(regionName, query);
    }
}
