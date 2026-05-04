package cn.kuship.console.modules.misc.webhook.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.api.ServiceLifecycleOperations;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.misc.webhook.security.WebhookDeliveryDeduper;
import cn.kuship.console.modules.misc.webhook.security.WebhookSignatureVerifier;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Webhook 触发部署：3 个公开端点（git/image/custom）。
 *
 * <p>Authentication priority (harden-webhook-hmac):
 * <ol>
 *   <li>git: {@code X-Hub-Signature-256} (GitHub HMAC) → {@code X-Gitlab-Token} (GitLab) → secret query (deprecated)</li>
 *   <li>image: {@code Authorization: Bearer} (Harbor) → secret query (deprecated)</li>
 *   <li>custom: {@code X-Kuship-Signature} (HMAC) → secret query (deprecated)</li>
 * </ol>
 *
 * <p>If any header signature is present but invalid, the request is rejected without falling
 * back to the secret query (header presence implies the caller intends header auth, so
 * silently accepting the query would mask configuration bugs). Successful verification +
 * passing the {@link WebhookDeliveryDeduper} check leads to a single
 * {@code lifecycleOps.upgradeService} call.
 */
@RestController
public class WebhookTriggerController {

    private static final Logger log = LoggerFactory.getLogger(WebhookTriggerController.class);

    private static final String HDR_GITHUB_SIG = "X-Hub-Signature-256";
    private static final String HDR_GITHUB_DELIVERY = "X-GitHub-Delivery";
    private static final String HDR_GITLAB_TOKEN = "X-Gitlab-Token";
    private static final String HDR_GITLAB_UUID = "X-Gitlab-Event-UUID";
    private static final String HDR_HARBOR_AUTH = "Authorization";
    private static final String HDR_KUSHIP_SIG = "X-Kuship-Signature";
    private static final String HDR_KUSHIP_DELIVERY = "X-Kuship-Delivery";

    private final TenantServiceRepository serviceRepo;
    private final ServiceLifecycleOperations lifecycleOps;
    private final WebhookSignatureVerifier verifier;
    private final WebhookDeliveryDeduper deduper;

    public WebhookTriggerController(TenantServiceRepository serviceRepo,
                                       ServiceLifecycleOperations lifecycleOps,
                                       WebhookSignatureVerifier verifier,
                                       WebhookDeliveryDeduper deduper) {
        this.serviceRepo = serviceRepo;
        this.lifecycleOps = lifecycleOps;
        this.verifier = verifier;
        this.deduper = deduper;
    }

    @PostMapping(value = {"/console/webhooks/{service_id}", "/console/webhooks/{service_id}/"})
    public ApiResult gitWebhook(@PathVariable("service_id") String serviceId,
                                    @RequestParam(value = "secret", required = false) String secret,
                                    @RequestBody(required = false) byte[] body,
                                    HttpServletRequest request) {
        TenantService s = requireService(serviceId);
        String githubSig = request.getHeader(HDR_GITHUB_SIG);
        String gitlabToken = request.getHeader(HDR_GITLAB_TOKEN);

        if (githubSig != null) {
            if (!verifier.verifyGitHub(body == null ? new byte[0] : body, githubSig, s.getSecret())) {
                throw new ServiceHandleException(401, "signature mismatch", "签名校验失败");
            }
        } else if (gitlabToken != null) {
            if (!verifier.verifyGitLab(gitlabToken, s.getSecret())) {
                throw new ServiceHandleException(401, "signature mismatch", "签名校验失败");
            }
        } else {
            verifyQueryFallback(s, secret, "git");
        }

        String deliveryId = firstNonBlank(request.getHeader(HDR_GITHUB_DELIVERY),
                request.getHeader(HDR_GITLAB_UUID));
        return triggerAfterAuth(s, "git", deliveryId);
    }

    @PostMapping(value = {"/console/image/webhooks/{service_id}", "/console/image/webhooks/{service_id}/"})
    public ApiResult imageWebhook(@PathVariable("service_id") String serviceId,
                                       @RequestParam(value = "secret", required = false) String secret,
                                       @RequestBody(required = false) byte[] body,
                                       HttpServletRequest request) {
        TenantService s = requireService(serviceId);
        String harborAuth = request.getHeader(HDR_HARBOR_AUTH);

        if (harborAuth != null) {
            if (!verifier.verifyHarbor(harborAuth, s.getSecret())) {
                throw new ServiceHandleException(401, "signature mismatch", "签名校验失败");
            }
        } else {
            verifyQueryFallback(s, secret, "image");
        }

        // Harbor's webhook payload doesn't carry a delivery ID — image-side dedup degrades
        // to "no dedup" until callers send X-Kuship-Delivery, which is intentional.
        String deliveryId = request.getHeader(HDR_KUSHIP_DELIVERY);
        return triggerAfterAuth(s, "image", deliveryId);
    }

    @PostMapping(value = {"/console/custom/deploy/{service_id}", "/console/custom/deploy/{service_id}/"})
    public ApiResult customWebhook(@PathVariable("service_id") String serviceId,
                                        @RequestParam(value = "secret", required = false) String secret,
                                        @RequestBody(required = false) byte[] body,
                                        HttpServletRequest request) {
        TenantService s = requireService(serviceId);
        String kuShipSig = request.getHeader(HDR_KUSHIP_SIG);

        if (kuShipSig != null) {
            if (!verifier.verifyCustom(body == null ? new byte[0] : body, kuShipSig, s.getSecret())) {
                throw new ServiceHandleException(401, "signature mismatch", "签名校验失败");
            }
        } else {
            verifyQueryFallback(s, secret, "custom");
        }

        String deliveryId = request.getHeader(HDR_KUSHIP_DELIVERY);
        return triggerAfterAuth(s, "custom", deliveryId);
    }

    private TenantService requireService(String serviceId) {
        return serviceRepo.findByServiceId(serviceId)
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
    }

    private void verifyQueryFallback(TenantService s, String secret, String kind) {
        if (!Objects.equals(s.getSecret(), secret)) {
            throw new ServiceHandleException(401, "secret mismatch", "secret 校验失败");
        }
        log.warn("webhook {} for service {} using deprecated query secret; switch to header signature",
                kind, s.getServiceId());
    }

    private ApiResult triggerAfterAuth(TenantService s, String kind, String deliveryId) {
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("service_id", s.getServiceId());
        bean.put("kind", kind);

        if (!deduper.tryAccept(s.getServiceId(), deliveryId)) {
            bean.put("triggered", false);
            bean.put("dedup", true);
            return GeneralMessage.ok(bean);
        }

        try {
            // MVP：触发 region restart 模拟 redeploy；正式 build_from_image 流程后续 hardening
            lifecycleOps.upgradeService(s.getServiceRegion(), "", s.getServiceAlias(), Map.of());
            bean.put("triggered", true);
        } catch (Exception e) {
            bean.put("triggered", false);
            bean.put("error", e.getMessage());
        }
        return GeneralMessage.ok(bean);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
