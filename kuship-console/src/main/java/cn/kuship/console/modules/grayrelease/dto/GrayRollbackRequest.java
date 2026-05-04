package cn.kuship.console.modules.grayrelease.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GrayRollbackRequest(
        @JsonProperty("template_id") String templateId) {
}
