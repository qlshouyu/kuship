package cn.kuship.console.modules.application.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建应用（service_group）请求 DTO。
 *
 * <p>字段命名兼容：rainbond-ui 历史以 {@code app_name} 提交（见 {@code services/application.js::addGroup}），
 * 也有部分入口用 {@code group_name}（rainbond-console DRF serializer 实际两者都吃）。kuship-console 通过
 * {@link JsonAlias} 同时接受两种命名，避免 form 校验在新建组件 + 安装流程上挂掉。
 */
public record CreateGroupReq(
        @JsonProperty("group_name") @JsonAlias("app_name") @NotBlank @Size(max = 128) String groupName,
        @Size(max = 2048) String note,
        @JsonProperty("region_name") @NotBlank @Size(max = 64) String regionName,
        @JsonProperty("k8s_app") @Size(max = 64) String k8sApp,
        @JsonProperty("governance_mode") @Size(max = 255) String governanceMode) {
}
