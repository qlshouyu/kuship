package cn.kuship.console.modules.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProbeReq(
        @NotBlank @Size(max = 20) String mode,
        @Size(max = 10) String scheme,
        @Size(max = 200) String path,
        Integer port,
        @Size(max = 1024) String cmd,
        @JsonProperty("http_header") @Size(max = 300) String httpHeader,
        @JsonProperty("initial_delay_second") Integer initialDelaySecond,
        @JsonProperty("period_second") Integer periodSecond,
        @JsonProperty("timeout_second") Integer timeoutSecond,
        @JsonProperty("failure_threshold") Integer failureThreshold,
        @JsonProperty("success_threshold") Integer successThreshold,
        @JsonProperty("is_used") Boolean used) {
}
