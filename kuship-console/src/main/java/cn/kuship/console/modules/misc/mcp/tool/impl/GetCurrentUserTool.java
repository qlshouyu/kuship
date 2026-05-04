package cn.kuship.console.modules.misc.mcp.tool.impl;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.account.repository.UserInfoRepository;
import cn.kuship.console.modules.misc.mcp.tool.McpTool;
import cn.kuship.console.modules.misc.mcp.tool.McpToolException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GetCurrentUserTool implements McpTool {

    private final UserInfoRepository userInfoRepo;
    private final ObjectMapper json;

    public GetCurrentUserTool(UserInfoRepository userInfoRepo, ObjectMapper json) {
        this.userInfoRepo = userInfoRepo;
        this.json = json;
    }

    @Override public String name() { return "get_current_user"; }

    @Override public String description() {
        return "Return the currently authenticated kuship user (user_id, nick_name, email, enterprise_id).";
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(), "additionalProperties", false);
    }

    @Override public JsonNode call(JsonNode arguments, RequestContext ctx) {
        if (ctx.getUserId() == null) {
            throw McpToolException.invalidParams("no authenticated user in request context");
        }
        UserInfo user = userInfoRepo.findById(ctx.getUserId())
                .orElseThrow(() -> McpToolException.internal("user not found: " + ctx.getUserId()));
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("user_id", user.getUserId());
        bean.put("nick_name", user.getNickName());
        bean.put("email", user.getEmail());
        bean.put("enterprise_id", user.getEnterpriseId());
        bean.put("sys_admin", Boolean.TRUE.equals(user.getSysAdmin()));
        return json.valueToTree(bean);
    }
}
