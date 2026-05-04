package cn.kuship.console.modules.grayrelease.dto;

import cn.kuship.console.modules.grayrelease.entity.GrayReleaseRecord;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record GrayReleaseRecordDto(
        @JsonProperty("id") Integer id,
        @JsonProperty("tenant_id") String tenantId,
        @JsonProperty("region_name") String regionName,
        @JsonProperty("app_id") Integer appId,
        @JsonProperty("app_name") String appName,
        @JsonProperty("template_id") String templateId,
        @JsonProperty("template_name") String templateName,
        @JsonProperty("template_version") String templateVersion,
        @JsonProperty("original_service_id") String originalServiceId,
        @JsonProperty("original_service_cname") String originalServiceCname,
        @JsonProperty("original_weight") Integer originalWeight,
        @JsonProperty("new_service_id") String newServiceId,
        @JsonProperty("new_service_cname") String newServiceCname,
        @JsonProperty("new_weight") Integer newWeight,
        @JsonProperty("domain_name") String domainName,
        @JsonProperty("gray_ratio") Integer grayRatio,
        @JsonProperty("status") String status,
        @JsonProperty("create_time") LocalDateTime createTime,
        @JsonProperty("update_time") LocalDateTime updateTime) {

    public static GrayReleaseRecordDto from(GrayReleaseRecord r) {
        int ratio = r.getGrayRatio() == null ? 0 : r.getGrayRatio();
        return new GrayReleaseRecordDto(
                r.getId(), r.getTenantId(), r.getRegionName(),
                r.getAppId(), r.getAppName(),
                r.getTemplateId(), r.getTemplateName(), r.getTemplateVersion(),
                r.getOriginalServiceId(), r.getOriginalServiceCname(), 100 - ratio,
                r.getGrayServiceId(), r.getGrayServiceCname(), ratio,
                r.getDomainName(), ratio,
                r.getStatus() == null ? null : r.getStatus().value(),
                r.getCreateTime(), r.getUpdateTime());
    }
}
