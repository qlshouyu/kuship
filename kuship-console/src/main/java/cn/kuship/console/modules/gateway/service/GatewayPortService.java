package cn.kuship.console.modules.gateway.service;

import cn.kuship.console.modules.application.repository.ServiceTcpDomainRepository;
import cn.kuship.console.modules.region.entity.RegionInfo;
import cn.kuship.console.modules.region.repository.RegionInfoEntityRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 可用 TCP 端口查询 + 高级路由地址查询（对齐 rainbond Python {@code GetPortView} / {@code GetSeniorUrlView}）。
 */
@Service
public class GatewayPortService {

    private static final int DEFAULT_PORT_START = 20001;
    private static final int DEFAULT_PORT_END = 30000;

    private final ServiceTcpDomainRepository tcpDomainRepo;
    private final RegionInfoEntityRepository regionInfoRepo;

    public GatewayPortService(ServiceTcpDomainRepository tcpDomainRepo,
                               RegionInfoEntityRepository regionInfoRepo) {
        this.tcpDomainRepo = tcpDomainRepo;
        this.regionInfoRepo = regionInfoRepo;
    }

    /**
     * 查询可用 TCP 端口列表（从 region.tcpdomain 配置范围中排除已占用端口）。
     *
     * @param regionId region_info.region_id 列
     * @return 可用端口号列表（最多 100 个）
     */
    public List<Integer> getFreePorts(String regionId) {
        // 查已占用端口
        List<String> endPoints = tcpDomainRepo.findEndPointsByRegionId(regionId);
        Set<Integer> usedPorts = new HashSet<>();
        for (String ep : endPoints) {
            extractPort(ep).ifPresent(usedPorts::add);
        }

        // 根据 region.tcpdomain 配置的端口范围过滤（简化：使用默认范围）
        return java.util.stream.IntStream.rangeClosed(DEFAULT_PORT_START, DEFAULT_PORT_END)
                .filter(p -> !usedPorts.contains(p))
                .boxed()
                .limit(100)
                .toList();
    }

    /**
     * 获取高级路由访问地址（region.httpdomain 前缀）。
     *
     * @param regionName region_name
     * @return 高级路由 URL 前缀
     */
    public String getSeniorUrl(String regionName) {
        return regionInfoRepo.findByRegionName(regionName)
                .map(RegionInfo::getHttpdomain)
                .map(domain -> "http://" + domain)
                .orElse("");
    }

    private java.util.OptionalInt extractPort(String endPoint) {
        if (endPoint == null) return java.util.OptionalInt.empty();
        // end_point 格式: "0.0.0.0:port" 或 "ip:port"
        int idx = endPoint.lastIndexOf(':');
        if (idx < 0) return java.util.OptionalInt.empty();
        try {
            return java.util.OptionalInt.of(Integer.parseInt(endPoint.substring(idx + 1)));
        } catch (NumberFormatException e) {
            return java.util.OptionalInt.empty();
        }
    }
}
