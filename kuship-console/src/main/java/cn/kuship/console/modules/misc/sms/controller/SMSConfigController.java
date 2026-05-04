package cn.kuship.console.modules.misc.sms.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.entity.ConsoleConfig;
import cn.kuship.console.modules.account.repository.ConsoleConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Enterprise-scoped SMS configuration (add-aliyun-sms).
 *
 * <p>Reads / writes the {@code enterprise.{eid}.SMS_CONFIG} key in {@code console_config}.
 * The first version of this controller persists overrides only — the actual provider still
 * resolves via global {@code kuship.sms.aliyun.*} config. Wiring this through to runtime
 * {@code AliyunSmsProvider.send} is left to a future {@code add-multi-tenant-sms} hardening.
 */
@RestController
@RequestMapping("/console/enterprises/{enterprise_id}/sms-config")
public class SMSConfigController {

    private static final Logger log = LoggerFactory.getLogger(SMSConfigController.class);
    private static final String KEY_PREFIX = "enterprise.";
    private static final String KEY_SUFFIX = ".SMS_CONFIG";

    private final ConsoleConfigRepository configRepo;
    private final ObjectMapper objectMapper;

    public SMSConfigController(ConsoleConfigRepository configRepo, ObjectMapper objectMapper) {
        this.configRepo = configRepo;
        this.objectMapper = objectMapper;
    }

    @GetMapping(value = {"", "/"})
    public ApiResult get(@PathVariable("enterprise_id") String enterpriseId) {
        return configRepo.findByKey(buildKey(enterpriseId))
                .map(c -> {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> parsed = c.getValue() == null
                                ? Map.of()
                                : objectMapper.readValue(c.getValue(), Map.class);
                        Map<String, Object> bean = new LinkedHashMap<>(parsed);
                        bean.put("enterprise_id", enterpriseId);
                        bean.put("enabled", true);
                        return GeneralMessage.ok(bean);
                    } catch (Exception e) {
                        log.warn("malformed SMS_CONFIG for enterprise {}: {}", enterpriseId, e.toString());
                        return GeneralMessage.ok(Map.of("enterprise_id", enterpriseId, "enabled", false,
                                "error", "stored config is not valid JSON"));
                    }
                })
                .orElseGet(() -> GeneralMessage.ok(Map.of(
                        "enterprise_id", enterpriseId,
                        "enabled", false,
                        "provider", "(unset, global kuship.sms.* applies)")));
    }

    @PutMapping(value = {"", "/"})
    @Transactional
    public ApiResult update(@PathVariable("enterprise_id") String enterpriseId,
                              @RequestBody(required = false) Map<String, Object> body) {
        String key = buildKey(enterpriseId);
        String json;
        try {
            json = objectMapper.writeValueAsString(body == null ? Map.of() : body);
        } catch (Exception e) {
            return GeneralMessage.ok(Map.of("updated", false, "error", "invalid body json"));
        }
        ConsoleConfig config = configRepo.findByKey(key).orElseGet(() -> {
            ConsoleConfig c = new ConsoleConfig();
            c.setKey(key);
            c.setUserNickName(""); // global scope, per ConsoleConfig convention
            c.setDescription("Enterprise " + enterpriseId + " SMS provider override");
            return c;
        });
        config.setValue(json);
        config.setUpdateTime(LocalDateTime.now());
        configRepo.save(config);
        return GeneralMessage.ok(Map.of(
                "enterprise_id", enterpriseId,
                "updated", true,
                "notice", "stored as enterprise override; global kuship.sms.* still wins at runtime "
                        + "until add-multi-tenant-sms hardening lands"));
    }

    private static String buildKey(String enterpriseId) {
        return KEY_PREFIX + enterpriseId + KEY_SUFFIX;
    }
}
