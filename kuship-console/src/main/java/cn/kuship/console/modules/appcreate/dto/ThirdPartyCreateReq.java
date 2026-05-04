package cn.kuship.console.modules.appcreate.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record ThirdPartyCreateReq(
        @JsonProperty("service_cname") @NotBlank @Size(max = 100) String serviceCname,
        List<Map<String, Object>> endpoints,
        @JsonProperty("group_id") Integer groupId,
        @JsonProperty("region_name") @NotBlank String regionName,
        @JsonProperty("k8s_component_name") @Size(max = 100) String k8sComponentName,
        @Size(max = 64) String kind) {
}
