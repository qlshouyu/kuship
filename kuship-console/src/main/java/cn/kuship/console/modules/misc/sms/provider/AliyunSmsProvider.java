package cn.kuship.console.modules.misc.sms.provider;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsResponseBody;
import com.aliyun.teaopenapi.models.Config;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Aliyun dysmsapi-backed provider (add-aliyun-sms).
 *
 * <p>Activated when {@code kuship.sms.provider=aliyun}. The {@link Client} is constructed once at
 * {@code @PostConstruct} time so all 4 required config keys must be populated before the bean
 * initialises — missing keys raise {@link IllegalStateException} and Spring fails the context.
 *
 * <p>Aliyun's SMS contract is template-driven: the actual text is approved on the aliyun console
 * and referenced via {@code template-code}. We only fill a single {@code code} parameter, which
 * lets the same template be reused across login / register / password-reset flows without
 * triggering re-approval.
 */
@Component
@ConditionalOnProperty(name = "kuship.sms.provider", havingValue = "aliyun")
public class AliyunSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(AliyunSmsProvider.class);

    private final String accessKeyId;
    private final String accessKeySecret;
    private final String signName;
    private final String templateCode;
    private final String endpoint;

    private Client client;

    public AliyunSmsProvider(
            @Value("${kuship.sms.aliyun.access-key-id:}") String accessKeyId,
            @Value("${kuship.sms.aliyun.access-key-secret:}") String accessKeySecret,
            @Value("${kuship.sms.aliyun.sign-name:}") String signName,
            @Value("${kuship.sms.aliyun.template-code:}") String templateCode,
            @Value("${kuship.sms.aliyun.endpoint:dysmsapi.aliyuncs.com}") String endpoint) {
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.signName = signName;
        this.templateCode = templateCode;
        this.endpoint = endpoint;
    }

    @PostConstruct
    void init() throws Exception {
        if (isBlank(accessKeyId)) {
            throw new IllegalStateException("aliyun-sms: kuship.sms.aliyun.access-key-id is required");
        }
        if (isBlank(accessKeySecret)) {
            throw new IllegalStateException("aliyun-sms: kuship.sms.aliyun.access-key-secret is required");
        }
        if (isBlank(signName)) {
            throw new IllegalStateException("aliyun-sms: kuship.sms.aliyun.sign-name is required");
        }
        if (isBlank(templateCode)) {
            throw new IllegalStateException("aliyun-sms: kuship.sms.aliyun.template-code is required");
        }
        Config config = new Config()
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret)
                .setEndpoint(endpoint);
        this.client = new Client(config);
        log.info("aliyun-sms provider initialised, endpoint={}", endpoint);
    }

    @Override
    public SmsResult send(String phone, String code, String purpose) {
        SendSmsRequest req = new SendSmsRequest()
                .setPhoneNumbers(phone)
                .setSignName(signName)
                .setTemplateCode(templateCode)
                .setTemplateParam("{\"code\":\"" + code + "\"}");
        try {
            SendSmsResponse resp = client.sendSms(req);
            SendSmsResponseBody body = resp.getBody();
            if (body == null) {
                return SmsResult.fail("EMPTY_RESPONSE", "aliyun-sms returned empty body");
            }
            if ("OK".equalsIgnoreCase(body.getCode())) {
                return SmsResult.ok(body.getBizId());
            }
            return SmsResult.fail(body.getCode(), body.getMessage());
        } catch (Exception e) {
            log.error("aliyun-sms send failed phone={} purpose={} cause={}", phone, purpose, e.toString());
            return SmsResult.fail("EXCEPTION", e.getMessage());
        }
    }

    @Override
    public String identifier() {
        return "aliyun";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
