package cn.kuship.console.modules.misc.webhook.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.util.UuidGenerator;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.misc.webhook.entity.ServiceWebhooks;
import cn.kuship.console.modules.misc.webhook.repository.ServiceWebhooksRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Webhook 配置管理：4 endpoint（get-url / trigger / status / updatekey）。 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps/{service_alias}/webhooks")
public class WebhookManageController {

    private final TenantsRepository tenantsRepo;
    private final TenantServiceRepository serviceRepo;
    private final ServiceWebhooksRepository webhooksRepo;

    public WebhookManageController(TenantsRepository tenantsRepo,
                                      TenantServiceRepository serviceRepo,
                                      ServiceWebhooksRepository webhooksRepo) {
        this.tenantsRepo = tenantsRepo;
        this.serviceRepo = serviceRepo;
        this.webhooksRepo = webhooksRepo;
    }

    @GetMapping(value = {"/get-url", "/get-url/"})
    public ApiResult getUrl(@PathVariable("team_name") String teamName,
                              @PathVariable("service_alias") String alias) {
        TenantService s = requireService(teamName, alias);
        String secret = s.getSecret() == null ? "" : s.getSecret();
        String svcId = s.getServiceId();
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("service_id", svcId);
        bean.put("secret", secret);
        // v1 (deprecated, secret in URL) — kept until enforce-webhook-signatures removes the fallback.
        bean.put("git_webhook_url", "/console/webhooks/" + svcId + "?secret=" + secret);
        bean.put("image_webhook_url", "/console/image/webhooks/" + svcId + "?secret=" + secret);
        bean.put("custom_webhook_url", "/console/custom/deploy/" + svcId + "?secret=" + secret);
        // v2 (header signature) — preferred; the front-end should display these going forward.
        bean.put("git_webhook_url_v2", "/console/webhooks/" + svcId);
        bean.put("image_webhook_url_v2", "/console/image/webhooks/" + svcId);
        bean.put("custom_webhook_url_v2", "/console/custom/deploy/" + svcId);
        bean.put("signature_examples", buildSignatureExamples(svcId));
        return GeneralMessage.ok(bean);
    }

    /**
     * Curl recipes for the four supported header signature flavours. The values intentionally
     * use literal {@code <secret>} / {@code <body>} placeholders (instead of inlining the real
     * secret) so the response is safe to paste into chat/screenshots.
     */
    private static Map<String, String> buildSignatureExamples(String serviceId) {
        Map<String, String> examples = new LinkedHashMap<>();
        examples.put("github",
                "curl -X POST https://<host>/console/webhooks/" + serviceId
                        + " -H 'X-Hub-Signature-256: sha256=<hmac_sha256(secret, body)>'"
                        + " -H 'X-GitHub-Delivery: <uuid>' --data '<body>'");
        examples.put("gitlab",
                "curl -X POST https://<host>/console/webhooks/" + serviceId
                        + " -H 'X-Gitlab-Token: <secret>' -H 'X-Gitlab-Event-UUID: <uuid>'");
        examples.put("harbor",
                "curl -X POST https://<host>/console/image/webhooks/" + serviceId
                        + " -H 'Authorization: Bearer <secret>'");
        examples.put("custom",
                "curl -X POST https://<host>/console/custom/deploy/" + serviceId
                        + " -H 'X-Kuship-Signature: sha256=<hmac_sha256(secret, body)>'"
                        + " -H 'X-Kuship-Delivery: <uuid>' --data '<body>'");
        return examples;
    }

    @PostMapping(value = {"/trigger", "/trigger/"})
    public ApiResult trigger(@PathVariable("team_name") String teamName,
                                @PathVariable("service_alias") String alias) {
        TenantService s = requireService(teamName, alias);
        return GeneralMessage.ok(Map.of("service_id", s.getServiceId(), "triggered", true));
    }

    @GetMapping(value = {"/status", "/status/"})
    public ApiResult status(@PathVariable("team_name") String teamName,
                              @PathVariable("service_alias") String alias) {
        TenantService s = requireService(teamName, alias);
        List<ServiceWebhooks> rows = webhooksRepo.findByServiceId(s.getServiceId());
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("service_id", s.getServiceId());
        bean.put("webhooks", rows.stream().map(WebhookManageController::toBean).toList());
        return GeneralMessage.ok(bean);
    }

    @PutMapping(value = {"/updatekey", "/updatekey/"})
    @Transactional
    public ApiResult updateSecret(@PathVariable("team_name") String teamName,
                                       @PathVariable("service_alias") String alias) {
        TenantService s = requireService(teamName, alias);
        String newSecret = UuidGenerator.makeUuid().substring(0, 16);
        s.setSecret(newSecret);
        serviceRepo.save(s);
        return GeneralMessage.ok(Map.of("service_id", s.getServiceId(), "secret", newSecret));
    }

    private TenantService requireService(String teamName, String alias) {
        Tenants team = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        return serviceRepo.findByTenantIdAndServiceAlias(team.getTenantId(), alias)
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
    }

    static Map<String, Object> toBean(ServiceWebhooks w) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("webhooks_type", w.getWebhooksType());
        b.put("deploy_keyword", w.getDeployKeyword());
        b.put("trigger", w.getTrigger());
        b.put("state", w.getState());
        return b;
    }
}
