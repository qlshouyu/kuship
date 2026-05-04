package cn.kuship.console.modules.appcreate.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SourceCodeCreateReq(
        @JsonProperty("service_cname") @NotBlank @Size(max = 100) String serviceCname,
        @JsonProperty("git_url") @NotBlank @Size(max = 200) String gitUrl,
        @JsonProperty("code_version") @Size(max = 100) String codeVersion,
        @JsonProperty("build_strategy") @Size(max = 20) String buildStrategy,
        @Size(max = 40) String language,
        @Size(max = 255) String dockerfile,
        @JsonProperty("oauth_service_id") Integer oauthServiceId,
        @JsonProperty("git_username") @Size(max = 255) String gitUsername,
        @JsonProperty("git_password") @Size(max = 255) String gitPassword,
        @JsonProperty("group_id") Integer groupId,
        @JsonProperty("region_name") @NotBlank String regionName,
        @JsonProperty("k8s_component_name") @Size(max = 100) String k8sComponentName,
        @JsonProperty("min_cpu") Integer minCpu,
        @JsonProperty("min_memory") Integer minMemory,
        @Size(max = 32) String arch) {
}
