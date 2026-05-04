package cn.kuship.console.modules.appcreate.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DockerRunCreateReq(
        @JsonProperty("service_cname") @NotBlank @Size(max = 100) String serviceCname,
        @NotBlank @Size(max = 200) String image,
        @Size(max = 2048) String cmd,
        @JsonProperty("docker_cmd") @Size(max = 1024) String dockerCmd,
        Integer port,
        @Size(max = 15) String protocol,
        @JsonProperty("group_id") Integer groupId,
        @JsonProperty("region_name") @NotBlank String regionName,
        @JsonProperty("k8s_component_name") @Size(max = 100) String k8sComponentName,
        @JsonProperty("min_cpu") Integer minCpu,
        @JsonProperty("min_memory") Integer minMemory,
        @JsonProperty("extend_method") String extendMethod,
        @Size(max = 32) String arch) {
}
