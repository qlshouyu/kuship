package cn.kuship.console.modules.grayrelease.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateGrayRatioRequest(
        @JsonProperty("template_id") String templateId,
        @JsonProperty("gray_ratio") Integer grayRatio) {
}
