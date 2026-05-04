package cn.kuship.console.modules.region.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

public record UpdateRegionReq(
        @JsonProperty("region_alias") @Size(max = 64) String regionAlias,
        @Size(max = 200) String desc,
        @Size(max = 256) String url,
        @Size(max = 256) String wsurl,
        @JsonProperty("ssl_ca_cert") String sslCaCert,
        @JsonProperty("cert_file") String certFile,
        @JsonProperty("key_file") String keyFile) {
}
