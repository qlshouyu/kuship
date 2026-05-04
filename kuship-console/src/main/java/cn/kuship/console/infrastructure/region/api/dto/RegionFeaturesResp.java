package cn.kuship.console.infrastructure.region.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record RegionFeaturesResp(
        List<String> features,
        @JsonProperty("kubeblocks_enabled") Boolean kubeblocksEnabled,
        Map<String, Object> raw) {
}
