package cn.kuship.console.modules.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEnterpriseUserReq(
        @JsonProperty("user_name") @NotBlank @Size(max = 64) String nickName,
        @NotBlank @Email @Size(max = 128) String email,
        @NotBlank @Size(min = 8, max = 64) String password,
        @Size(max = 15) String phone,
        @JsonProperty("real_name") @Size(max = 64) String realName) {
}
