package cn.kuship.console.modules.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PortReq(
        @NotNull Integer port,
        @Size(max = 15) String protocol,
        @JsonProperty("port_alias") @Size(max = 64) String portAlias,
        @JsonProperty("is_inner_service") Boolean innerService,
        @JsonProperty("is_outer_service") Boolean outerService,
        @JsonProperty("k8s_service_name") @Size(max = 63) String k8sServiceName,
        @Size(max = 64) String name) {
}
