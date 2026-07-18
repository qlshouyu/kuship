package cn.kuship.console.modules.enterprise.service;

import cn.kuship.console.infrastructure.region.RegionClient;
import cn.kuship.console.infrastructure.region.TimeoutTier;
import cn.kuship.console.modules.region.entity.RegionConfig;
import cn.kuship.console.modules.region.repository.RegionConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 企业资源监控总览（对齐 rainbond EnterpriseMonitor.get）。
 * 累加各可用集群 /v2/cluster 的 cap_mem/req_mem/cap_cpu/req_cpu；单集群失败则跳过（降级）。
 * 注：本环境 region(rbd-api-api:8443) 在宿主机不可达，故内存/CPU 累加为 0；total_regions 取自 DB 计数（可达）。
 */
@Service
public class EnterpriseMonitorService {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseMonitorService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RegionConfigRepository regionConfigRepository;
    private final RegionClient regionClient;

    public EnterpriseMonitorService(RegionConfigRepository regionConfigRepository, RegionClient regionClient) {
        this.regionConfigRepository = regionConfigRepository;
        this.regionClient = regionClient;
    }

    /** 资源总览 bean；无可用集群返回 null（controller 走 404/no found 分支）。 */
    public Map<String, Object> monitor(String enterpriseId) {
        List<RegionConfig> regions = regionConfigRepository
                .findByEnterpriseIdAndStatusOrderById(enterpriseId, "1");
        if (regions.isEmpty()) {
            return null;
        }
        double memTotal = 0;
        double memUsed = 0;
        double cpuTotal = 0;
        double cpuUsed = 0;
        for (RegionConfig region : regions) {
            try {
                String body = regionClient.exchange(region.getRegionName(), "GET", "/v2/cluster",
                        null, TimeoutTier.NORMAL, null);
                Object beanObj = MAPPER.readValue(body, Map.class).get("bean");
                if (beanObj instanceof Map<?, ?> bean) {
                    memTotal += toDouble(bean.get("cap_mem"));
                    memUsed += toDouble(bean.get("req_mem"));
                    cpuTotal += toDouble(bean.get("cap_cpu"));
                    cpuUsed += toDouble(bean.get("req_cpu"));
                }
            } catch (Exception e) {
                log.debug("get region resources {}: {}", region.getRegionName(), e.getMessage());
            }
        }
        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("used", numify(memUsed));
        memory.put("total", numify(memTotal));
        Map<String, Object> cpu = new LinkedHashMap<>();
        cpu.put("used", numify(cpuUsed));
        cpu.put("total", numify(cpuTotal));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total_regions", regions.size());
        data.put("memory", memory);
        data.put("cpu", cpu);
        return data;
    }

    private static double toDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0d;
    }

    /** 整数值输出为 long、小数值保留 double（对齐 7070：cap_mem 等为整数，req_cpu 可能为 1.1）。
     * 注意：不能用三元，long/double 混用会被统一提升为 double（2816 → 2816.0）。 */
    private static Number numify(double v) {
        if (!Double.isInfinite(v) && v == Math.floor(v)) {
            return (long) v;
        }
        return v;
    }
}
