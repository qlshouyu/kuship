package cn.kuship.console.modules.misc.message.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.misc.message.entity.UserMessage;
import cn.kuship.console.modules.misc.message.repository.UserMessageRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code /teams/{team_name}/message}：用户消息中心。 */
@RestController
@RequestMapping("/console/teams/{team_name}/message")
public class UserMessageController {

    private final UserMessageRepository repo;
    private final RequestContext requestContext;

    public UserMessageController(UserMessageRepository repo, RequestContext requestContext) {
        this.repo = repo;
        this.requestContext = requestContext;
    }

    @GetMapping(value = {"", "/"})
    public ApiResult list(@PathVariable("team_name") String teamName,
                            @RequestParam(value = "is_read", required = false) Boolean isRead) {
        Integer userId = requestContext.getUserId();
        if (userId == null) {
            throw new ServiceHandleException(401, "missing user context", "未认证或 token 失效");
        }
        List<UserMessage> messages = isRead == null
                ? repo.findByReceiverIdOrderByCreateTimeDesc(userId)
                : repo.findByReceiverIdAndIsReadOrderByCreateTimeDesc(userId, isRead);
        return GeneralMessage.okList(messages.stream().map(UserMessageController::toBean).toList());
    }

    @PutMapping(value = {"", "/"})
    @Transactional
    @SuppressWarnings("unchecked")
    public ApiResult update(@PathVariable("team_name") String teamName,
                              @RequestBody Map<String, Object> body) {
        Integer userId = requestContext.getUserId();
        if (userId == null) {
            throw new ServiceHandleException(401, "missing user context", "未认证或 token 失效");
        }
        Object idsObj = body.get("message_ids");
        if (!(idsObj instanceof List<?> ids)) {
            throw new ServiceHandleException(400, "missing message_ids", "缺少 message_ids");
        }
        String action = String.valueOf(body.getOrDefault("action", "read"));
        List<String> messageIds = ids.stream().map(Object::toString).toList();
        List<UserMessage> rows = repo.findByMessageIdInAndReceiverId(messageIds, userId);
        switch (action) {
            case "read" -> rows.forEach(r -> {
                r.setIsRead(true);
                r.setUpdateTime(LocalDateTime.now());
            });
            case "unread" -> rows.forEach(r -> {
                r.setIsRead(false);
                r.setUpdateTime(LocalDateTime.now());
            });
            case "delete" -> {
                rows.forEach(repo::delete);
                return GeneralMessage.ok(Map.of("deleted", rows.size()));
            }
            default -> throw new ServiceHandleException(400, "invalid action", "action 必须是 read/unread/delete");
        }
        repo.saveAll(rows);
        return GeneralMessage.ok(Map.of("updated", rows.size()));
    }

    static Map<String, Object> toBean(UserMessage m) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("message_id", m.getMessageId());
        b.put("title", m.getTitle());
        b.put("content", m.getContent());
        b.put("level", m.getLevel());
        b.put("msg_type", m.getMsgType());
        b.put("is_read", m.getIsRead());
        b.put("announcement_id", m.getAnnouncementId());
        b.put("create_time", m.getCreateTime());
        return b;
    }
}
