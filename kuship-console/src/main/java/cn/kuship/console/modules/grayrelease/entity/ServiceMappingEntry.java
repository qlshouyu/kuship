package cn.kuship.console.modules.grayrelease.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ServiceMappingEntry(
        @JsonProperty("original_service_id") String originalServiceId,
        @JsonProperty("original_service_cname") String originalServiceCname,
        @JsonProperty("gray_service_id") String grayServiceId,
        @JsonProperty("gray_service_cname") String grayServiceCname) {
}
