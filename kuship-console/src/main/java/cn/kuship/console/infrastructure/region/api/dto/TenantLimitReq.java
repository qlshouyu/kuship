package cn.kuship.console.infrastructure.region.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TenantLimitReq(
        @JsonProperty("limit_memory") Long limitMemory,
        @JsonProperty("limit_cpu") Long limitCpu,
        @JsonProperty("limit_storage") Long limitStorage) {
}
