package cn.kuship.console.modules.gateway.cert.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.gateway.cert.entity.ServiceDomainCertificate;
import cn.kuship.console.modules.gateway.cert.service.CertificateService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 证书单条管理（更新 / 删除）。
 *
 * <p>对齐 rainbond {@code urls.py:631-632} {@code TenantCertificateManageView}：
 * <ul>
 *   <li>PUT    /console/teams/{team_name}/certificates/{certificate_id}</li>
 *   <li>DELETE /console/teams/{team_name}/certificates/{certificate_id}</li>
 * </ul>
 *
 * <p>注意：rainbond 端此 view 仅有 put + delete，无 get 方法（设计确认见 design.md 修订段落），
 * 本 controller 同样不实现 GET，详情通过列表 + 客户端过滤完成。
 *
 * <p>路径变量 {@code certificate_id} 在 rainbond 实现中实际传递的是整型主键 ID
 * （view 使用 {@code kwargs.get("certificate_id")} 直传 pk），本 controller 同此。
 */
@RestController
@RequestMapping({
        "/console/teams/{team_name}/certificates/{certificate_id}",
        "/console/teams/{team_name}/certificates/{certificate_id}/"
})
public class TenantCertificateManageController {

    private final TenantsRepository tenantsRepo;
    private final CertificateService certService;

    public TenantCertificateManageController(TenantsRepository tenantsRepo,
                                              CertificateService certService) {
        this.tenantsRepo = tenantsRepo;
        this.certService = certService;
    }

    /**
     * 更新证书（alias / certificate / private_key / certificate_type 均可选）。
     *
     * @param teamName      团队名
     * @param certificateId 证书主键 ID（路径变量）
     * @param body          更新字段 Map
     * @return general_message 包装的更新后实体
     */
    @PutMapping
    @RequirePerm(PermCode.TEAM_CERTIFICATE)
    public Object updateCertificate(
            @PathVariable("team_name") String teamName,
            @PathVariable("certificate_id") Integer certificateId,
            @RequestBody Map<String, Object> body) {
        Tenants tenant = requireTenant(teamName);

        String alias = (String) body.get("alias");
        String certificate = (String) body.get("certificate");
        String privateKey = (String) body.get("private_key");
        String certificateType = (String) body.get("certificate_type");
        String regionName = (String) body.getOrDefault("region_name", "");

        ServiceDomainCertificate updated = certService.updateCertificate(
                regionName, tenant, certificateId, alias, certificate, privateKey, certificateType);

        Map<String, Object> bean = Map.of(
                "id", updated.getId(),
                "alias", updated.getAlias(),
                "certificate_type", updated.getCertificateType()
        );
        return GeneralMessage.ok(bean);
    }

    /**
     * 删除证书（被 service_domain 引用时返回 409）。
     *
     * @param teamName      团队名
     * @param certificateId 证书主键 ID（路径变量）
     * @return general_message 成功响应
     */
    @DeleteMapping
    @RequirePerm(PermCode.TEAM_CERTIFICATE)
    public Object deleteCertificate(
            @PathVariable("team_name") String teamName,
            @PathVariable("certificate_id") Integer certificateId,
            @RequestBody(required = false) Map<String, Object> body) {
        Tenants tenant = requireTenant(teamName);
        String regionName = body != null ? (String) body.getOrDefault("region_name", "") : "";

        certService.deleteCertificate(regionName, tenant, certificateId);
        return GeneralMessage.ok();
    }

    private Tenants requireTenant(String teamName) {
        return tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404,
                        "team not found: " + teamName, "团队不存在"));
    }
}
