package cn.kuship.console.modules.misc.sms.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.misc.sms.entity.SmsVerificationCode;
import cn.kuship.console.modules.misc.sms.provider.SmsProvider;
import cn.kuship.console.modules.misc.sms.repository.SmsVerificationCodeRepository;
import cn.kuship.console.modules.misc.sms.security.SmsRateLimiter;
import cn.kuship.console.modules.misc.sms.security.SmsVerifyFailureLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * SMS 短信验证码 + 手机号注册/登录（add-aliyun-sms）。
 *
 * <p>Send path: rate-limit pre-check → DB insert (transactional) → SmsProvider.send → on
 * provider failure roll the transaction back so we don't leave dangling codes that no one ever
 * received. The provider may be {@code LoggingSmsProvider} (dev) or {@code AliyunSmsProvider}
 * (prod) — picked at startup via {@code kuship.sms.provider}.
 *
 * <p>Verify path: failure limiter pre-check (5-min / 5-misses sliding window) → DB compare.
 * Successful verify resets the failure counter so the next legitimate retry isn't punished.
 */
@RestController
public class SMSVerificationController {

    private static final Logger log = LoggerFactory.getLogger(SMSVerificationController.class);
    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    private final SmsVerificationCodeRepository repo;
    private final SmsProvider smsProvider;
    private final SmsRateLimiter rateLimiter;
    private final SmsVerifyFailureLimiter failureLimiter;

    public SMSVerificationController(SmsVerificationCodeRepository repo,
                                          SmsProvider smsProvider,
                                          SmsRateLimiter rateLimiter,
                                          SmsVerifyFailureLimiter failureLimiter) {
        this.repo = repo;
        this.smsProvider = smsProvider;
        this.rateLimiter = rateLimiter;
        this.failureLimiter = failureLimiter;
    }

    @PostMapping(value = {"/console/sms/send-code", "/console/sms/send-code/"})
    @Transactional
    public ApiResult sendCode(@RequestBody Map<String, Object> body) {
        String phone = String.valueOf(body.getOrDefault("phone", ""));
        if (!PHONE.matcher(phone).matches()) {
            throw new ServiceHandleException(400, "invalid phone", "手机号格式不正确");
        }
        if (!rateLimiter.tryAcquire(phone)) {
            throw new ServiceHandleException(429, "rate limited", "发送过于频繁，请稍后再试");
        }
        String purpose = String.valueOf(body.getOrDefault("purpose", "login"));
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        SmsVerificationCode entity = new SmsVerificationCode();
        entity.setPhone(phone);
        entity.setCode(code);
        entity.setPurpose(purpose);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        repo.save(entity);

        SmsProvider.SmsResult result = smsProvider.send(phone, code, purpose);
        if (!result.success()) {
            log.error("sms send failed phone={} provider={} errorCode={} errorMessage={}",
                    phone, smsProvider.identifier(), result.errorCode(), result.errorMessage());
            // Roll back the inserted row so we don't leave a code the user never received.
            throw new ServiceHandleException(502, "sms send failed: " + result.errorCode(),
                    "短信发送失败，请稍后重试");
        }
        return GeneralMessage.ok(Map.of(
                "sent", true,
                "provider", smsProvider.identifier(),
                "expires_at", entity.getExpiresAt()));
    }

    @PostMapping(value = {"/console/users/register-by-phone", "/console/users/register-by-phone/"})
    public ApiResult registerByPhone(@RequestBody Map<String, Object> body) {
        verifyCode(body, "register");
        // MVP：返回 stub 注册响应；真实注册流程需调 user_info INSERT + 签发 JWT，留作 hardening
        return GeneralMessage.ok(Map.of(
                "phone", body.get("phone"),
                "registered", true,
                "notice", "register-by-phone stub; full integration with user_info pending"));
    }

    @PostMapping(value = {"/console/users/login-by-phone", "/console/users/login-by-phone/"})
    public ApiResult loginByPhone(@RequestBody Map<String, Object> body) {
        verifyCode(body, "login");
        return GeneralMessage.ok(Map.of(
                "phone", body.get("phone"),
                "logged_in", true,
                "notice", "login-by-phone stub; JWT issuance pending hardening"));
    }

    private void verifyCode(Map<String, Object> body, String purpose) {
        String phone = String.valueOf(body.getOrDefault("phone", ""));
        String code = String.valueOf(body.getOrDefault("code", ""));
        if (!PHONE.matcher(phone).matches() || code.length() != 6) {
            throw new ServiceHandleException(400, "invalid phone or code", "手机号或验证码格式不正确");
        }
        if (failureLimiter.isLocked(phone, purpose)) {
            throw new ServiceHandleException(429, "verify locked", "验证码已锁定，请 5 分钟后重试");
        }
        var matched = repo.findByPhoneAndPurposeAndExpiresAtAfterOrderByCreatedAtDesc(
                phone, purpose, LocalDateTime.now()).stream()
                .filter(c -> c.getCode().equals(code))
                .findFirst();
        if (matched.isEmpty()) {
            failureLimiter.recordFailure(phone, purpose);
            throw new ServiceHandleException(401, "code mismatch", "验证码错误或已过期");
        }
        failureLimiter.reset(phone, purpose);
    }
}
