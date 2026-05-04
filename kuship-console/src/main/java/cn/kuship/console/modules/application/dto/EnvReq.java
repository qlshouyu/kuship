package cn.kuship.console.modules.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnvReq(
        @Size(max = 1024) String name,
        @JsonProperty("attr_name") @NotBlank @Size(max = 1024) String attrName,
        @JsonProperty("attr_value") String attrValue,
        @JsonProperty("is_change") Boolean change,
        @Size(max = 10) String scope,
        @JsonProperty("container_port") Integer containerPort) {
}
