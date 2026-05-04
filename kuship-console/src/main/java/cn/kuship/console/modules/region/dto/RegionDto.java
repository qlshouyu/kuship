package cn.kuship.console.modules.region.dto;

import cn.kuship.console.modules.region.entity.RegionInfo;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/** 序列化 region 时按 level 脱敏（safe 隐藏 cert/key/token）。 */
public record RegionDto(
        @JsonProperty("region_id") String regionId,
        @JsonProperty("region_name") String regionName,
        @JsonProperty("region_alias") String regionAlias,
        @JsonProperty("region_type") List<String> regionType,
        String url,
        String wsurl,
        String httpdomain,
        String tcpdomain,
        String status,
        @JsonProperty("create_time") LocalDateTime createTime,
        String desc,
        String scope,
        @JsonProperty("enterprise_id") String enterpriseId,
        String provider,
        @JsonProperty("provider_cluster_id") String providerClusterId,
        @JsonProperty("ssl_ca_cert") String sslCaCert,
        @JsonProperty("cert_file") String certFile,
        @JsonProperty("key_file") String keyFile,
        String token) {

    public static RegionDto fromSafe(RegionInfo r) {
        return new RegionDto(
                r.getRegionId(), r.getRegionName(), r.getRegionAlias(),
                parseTypes(r.getRegionType()), r.getUrl(), r.getWsurl(),
                r.getHttpdomain(), r.getTcpdomain(), r.getStatus(), r.getCreateTime(),
                r.getDescription(), r.getScope(), r.getEnterpriseId(), r.getProvider(),
                r.getProviderClusterId(),
                masked(r.getSslCaCert()), masked(r.getCertFile()), masked(r.getKeyFile()),
                masked(r.getToken()));
    }

    public static RegionDto fromFull(RegionInfo r) {
        return new RegionDto(
                r.getRegionId(), r.getRegionName(), r.getRegionAlias(),
                parseTypes(r.getRegionType()), r.getUrl(), r.getWsurl(),
                r.getHttpdomain(), r.getTcpdomain(), r.getStatus(), r.getCreateTime(),
                r.getDescription(), r.getScope(), r.getEnterpriseId(), r.getProvider(),
                r.getProviderClusterId(),
                r.getSslCaCert(), r.getCertFile(), r.getKeyFile(), r.getToken());
    }

    private static String masked(String s) {
        if (s == null || s.isBlank()) return null;
        return "***" + s.length() + " chars***";
    }

    private static List<String> parseTypes(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            tools.jackson.databind.json.JsonMapper m = tools.jackson.databind.json.JsonMapper.builder().build();
            tools.jackson.databind.JsonNode arr = m.readTree(json);
            if (arr.isArray()) {
                java.util.ArrayList<String> out = new java.util.ArrayList<>();
                arr.forEach(n -> out.add(n.asText("")));
                return out;
            }
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }
}
