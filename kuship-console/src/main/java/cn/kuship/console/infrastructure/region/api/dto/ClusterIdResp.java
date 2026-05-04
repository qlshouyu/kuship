package cn.kuship.console.infrastructure.region.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClusterIdResp(
        @JsonProperty("cluster_id") String clusterId) {
}
