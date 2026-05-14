package cn.kuship.console.modules.gateway.cert.controller;

import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.gateway.cert.service.CertificateService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 证书与域名匹配校验。
 *
 * <p>对齐 rainbond {@code urls.py:655} {@code CalibrationCertificate}：
 * <ul>
 *   <li>POST /console/teams/{team_name}/calibration_certificate</li>
 * </ul>
 *
 * <p>通配符规则（rainbond 行为）：{@code *.foo.com} 匹配子域 {@code bar.foo.com}，
 * 但不匹配根域 {@code foo.com}。
 */
@RestController
@RequestMapping({
        "/console/teams/{team_name}/calibration_certificate",
        "/console/teams/{team_name}/calibration_certificate/"
})
public class CalibrationCertificateController {

    private final CertificateService certService;

    public CalibrationCertificateController(CertificateService certService) {
        this.certService = certService;
    }

    /**
     * 校验证书是否覆盖指定域名。
     *
     * @param teamName 团队名（路径变量，不参与业务逻辑，仅用于鉴权上下文）
     * @param body     包含 {@code certificate_id}（Integer 主键）和 {@code domain_name}
     * @return general_message 包装的结果，{@code bean.is_pass} 为 {@code "pass"} 或 {@code "un_pass"}
     */
    @PostMapping
    @RequirePerm(PermCode.TEAM_CERTIFICATE)
    public Object checkCertificate(
            @PathVariable("team_name") String teamName,
            @RequestBody Map<String, Object> body) {
        Integer certificateId = ((Number) body.get("certificate_id")).intValue();
        String domainName = (String) body.get("domain_name");

        String result = certService.checkCertificate(certificateId, domainName);
        return GeneralMessage.ok(Map.of("is_pass", result));
    }
}
