package cn.kuship.console.modules.account.dto;

import cn.kuship.console.modules.account.entity.UserInfo;
import com.fasterxml.jackson.annotation.JsonProperty;

public record UserDetailDto(
        @JsonProperty("user_id") Integer userId,
        @JsonProperty("nick_name") String nickName,
        String email,
        String phone,
        @JsonProperty("real_name") String realName,
        @JsonProperty("is_user_enable") boolean enabled,
        @JsonProperty("enterprise_id") String enterpriseId,
        @JsonProperty("origin") String origin,
        String logo,
        @JsonProperty("sys_admin") boolean sysAdmin) {

    public static UserDetailDto from(UserInfo u) {
        return new UserDetailDto(
                u.getUserId(),
                u.getNickName(),
                u.getEmail(),
                u.getPhone(),
                u.getRealName(),
                Boolean.TRUE.equals(u.getActive()),
                u.getEnterpriseId(),
                "register",
                u.getLogo(),
                Boolean.TRUE.equals(u.getSysAdmin()));
    }
}
