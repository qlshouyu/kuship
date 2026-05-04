package cn.kuship.console.modules.grayrelease.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateGrayReleaseRequest(
        @JsonProperty("template_id") String templateId,
        @JsonProperty("template_version") String templateVersion,
        @JsonProperty("domain_name") String domainName,
        @JsonProperty("gray_ratio") Integer grayRatio,
        @JsonProperty("market_name") String marketName,
        @JsonProperty("install_from_cloud") Boolean installFromCloud) {
}
