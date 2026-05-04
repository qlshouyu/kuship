package cn.kuship.console.modules.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record DependencyReq(
        @JsonProperty("dep_service_id") @NotBlank String depServiceId,
        @JsonProperty("dep_order") Integer depOrder) {
}
