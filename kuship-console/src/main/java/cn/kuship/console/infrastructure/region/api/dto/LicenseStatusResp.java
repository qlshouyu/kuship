package cn.kuship.console.infrastructure.region.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record LicenseStatusResp(
        Boolean active,
        @JsonProperty("expire_time") String expireTime,
        @JsonProperty("max_node") Integer maxNode,
        @JsonProperty("max_memory") Long maxMemory,
        Map<String, Object> raw) {
}
