package cn.kuship.console.infrastructure.region.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record RegionResourceResp(
        @JsonProperty("region_name") String regionName,
        @JsonProperty("cap_cpu") Long capCpu,
        @JsonProperty("cap_mem") Long capMem,
        @JsonProperty("req_cpu") Long reqCpu,
        @JsonProperty("req_mem") Long reqMem,
        @JsonProperty("cap_disk") Long capDisk,
        @JsonProperty("req_disk") Long reqDisk,
        Map<String, Object> raw) {
}
