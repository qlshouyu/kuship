package cn.kuship.console.modules.region.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.util.UuidGenerator;
import cn.kuship.console.infrastructure.region.client.RegionClientFactory;
import cn.kuship.console.modules.account.entity.TenantRegionInfo;
import cn.kuship.console.modules.account.repository.TenantRegionInfoRepository;
import cn.kuship.console.modules.region.dto.AddRegionReq;
import cn.kuship.console.modules.region.dto.UpdateRegionReq;
import cn.kuship.console.modules.region.entity.RegionInfo;
import cn.kuship.console.modules.region.repository.RegionInfoEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Region CRUD + token 解析。复刻 rainbond `region_services.parse_token` / `add_region` / `del_region` 行为。 */
@Service
public class RegionService {

    private static final Logger log = LoggerFactory.getLogger(RegionService.class);

    private final RegionInfoEntityRepository repo;
    private final TenantRegionInfoRepository tenantRegionRepo;
    private final RegionClientFactory clientFactory;
    private final JsonMapper json = JsonMapper.builder().build();

    public RegionService(RegionInfoEntityRepository repo,
                          TenantRegionInfoRepository tenantRegionRepo,
                          RegionClientFactory clientFactory) {
        this.repo = repo;
        this.tenantRegionRepo = tenantRegionRepo;
        this.clientFactory = clientFactory;
    }

    /**
     * 解析 kubectl-format YAML token，错误顺序与 rainbond `parse_token` 完全对齐。
     */
    public RegionInfo parseToken(String yamlToken, String regionName, String regionAlias, List<String> regionType) {
        Map<String, Object> info;
        try {
            info = new Yaml().load(yamlToken);
        } catch (Exception e) {
            throw new ServiceHandleException(400, "parse yaml error", "Region Config 内容不是有效YAML格式");
        }
        if (info == null) {
            throw new ServiceHandleException(400, "parse yaml error", "Region Config 内容不是有效YAML格式");
        }
        requireField(info, "ca.pem", "CA证书不存在");
        requireField(info, "client.key.pem", "客户端密钥不存在");
        requireField(info, "client.pem", "客户端证书不存在");
        requireField(info, "apiAddress", "API地址不存在");
        requireField(info, "websocketAddress", "Websocket地址不存在");
        requireField(info, "defaultDomainSuffix", "HTTP默认域名后缀不存在");
        requireField(info, "defaultTCPHost", "TCP默认IP地址不存在");

        RegionInfo r = new RegionInfo();
        r.setRegionAlias(regionAlias);
        r.setRegionName(regionName);
        r.setRegionType(serializeRegionType(regionType));
        r.setSslCaCert(asString(info.get("ca.pem")));
        r.setKeyFile(asString(info.get("client.key.pem")));
        r.setCertFile(asString(info.get("client.pem")));
        r.setUrl(asString(info.get("apiAddress")));
        r.setWsurl(asString(info.get("websocketAddress")));
        r.setHttpdomain(asString(info.get("defaultDomainSuffix")));
        r.setTcpdomain(asString(info.get("defaultTCPHost")));
        r.setRegionId(UuidGenerator.makeUuid());
        return r;
    }

    @Transactional
    public RegionInfo addRegion(String enterpriseId, AddRegionReq req) {
        RegionInfo r = parseToken(req.token(), req.regionName(), req.regionAlias(), req.regionType());
        if (repo.findByRegionName(r.getRegionName()).isPresent()) {
            throw new ServiceHandleException(400, "region_name already exists", "集群名称已存在");
        }
        r.setEnterpriseId(enterpriseId);
        r.setDescription(req.desc());
        r.setStatus("1");
        r.setScope(req.scope() != null ? req.scope() : "private");
        r.setProvider(req.provider() != null ? req.provider() : "");
        r.setProviderClusterId(req.providerClusterId() != null ? req.providerClusterId() : "");
        r.setCreateTime(LocalDateTime.now());
        r.setToken(""); // not stored from request token; rainbond 兼容
        return repo.save(r);
    }

    @Transactional
    public RegionInfo updateRegion(String enterpriseId, String regionId, UpdateRegionReq req) {
        RegionInfo r = repo.findByRegionId(regionId)
                .orElseThrow(() -> new ServiceHandleException(404, "region not found", "集群不存在"));
        if (!enterpriseId.equals(r.getEnterpriseId())) {
            throw new ServiceHandleException(403, "region not in enterprise", "集群不属于该企业");
        }
        if (req.regionAlias() != null) r.setRegionAlias(req.regionAlias());
        if (req.desc() != null) r.setDescription(req.desc());
        if (req.url() != null) r.setUrl(req.url());
        if (req.wsurl() != null) r.setWsurl(req.wsurl());
        if (req.sslCaCert() != null) r.setSslCaCert(req.sslCaCert());
        if (req.certFile() != null) r.setCertFile(req.certFile());
        if (req.keyFile() != null) r.setKeyFile(req.keyFile());
        RegionInfo saved = repo.save(r);
        // 更新可能变了 cert/url；强制 evict client cache
        clientFactory.evict(enterpriseId, r.getRegionName());
        return saved;
    }

    @Transactional
    public void deleteRegion(String enterpriseId, String regionId) {
        RegionInfo r = repo.findByRegionId(regionId)
                .orElseThrow(() -> new ServiceHandleException(404, "region not found", "集群不存在"));
        if (!enterpriseId.equals(r.getEnterpriseId())) {
            throw new ServiceHandleException(403, "region not in enterprise", "集群不属于该企业");
        }
        // 校验 tenant_region 表中无该 region_name 的关联
        long usedBy = tenantRegionRepo.findAll().stream()
                .filter(tr -> r.getRegionName().equals(tr.getRegionName()) && Boolean.TRUE.equals(tr.getActive()))
                .count();
        if (usedBy > 0) {
            throw new ServiceHandleException(400, "region in use",
                    "该集群仍有团队在使用，请先关闭团队的集群关联");
        }
        repo.delete(r);
        clientFactory.evict(enterpriseId, r.getRegionName());
    }

    public List<RegionInfo> listByEnterprise(String enterpriseId, String status) {
        if (status != null && !status.isBlank()) {
            return repo.findByEnterpriseIdAndStatus(enterpriseId, status);
        }
        return repo.findByEnterpriseId(enterpriseId);
    }

    public RegionInfo requireRegion(String enterpriseId, String regionId) {
        RegionInfo r = repo.findByRegionId(regionId)
                .orElseThrow(() -> new ServiceHandleException(404, "region not found", "集群不存在"));
        if (!enterpriseId.equals(r.getEnterpriseId())) {
            throw new ServiceHandleException(403, "region not in enterprise", "集群不属于该企业");
        }
        return r;
    }

    private void requireField(Map<String, Object> info, String key, String msgShow) {
        Object v = info.get(key);
        if (v == null || (v instanceof String s && s.isBlank())) {
            throw new ServiceHandleException(400, key + " not found", msgShow);
        }
    }

    private static String asString(Object v) {
        return v == null ? null : v.toString();
    }

    private String serializeRegionType(List<String> types) {
        if (types == null) return "[]";
        try {
            return json.writeValueAsString(types);
        } catch (Exception e) {
            log.warn("region_type serialization fail: {}", e.getMessage());
            return "[]";
        }
    }
}
