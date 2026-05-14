package cn.kuship.console.modules.gateway.cert.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.gateway.cert.entity.ServiceDomainCertificate;
import cn.kuship.console.modules.gateway.cert.service.CertificateService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 团队证书列表与上传。
 *
 * <p>对齐 rainbond {@code console/urls/__init__.py:630} {@code TenantCertificateView}：
 * <ul>
 *   <li>GET  /console/teams/{team_name}/certificates?page_num=&page_size=&search_key=</li>
 *   <li>POST /console/teams/{team_name}/certificates</li>
 * </ul>
 *
 * <p>trailing slash 兼容（Spring 6 需显式列出）。
 */
@RestController
@RequestMapping({
        "/console/teams/{team_name}/certificates",
        "/console/teams/{team_name}/certificates/"
})
public class TenantCertificateController {

    private final TenantsRepository tenantsRepo;
    private final CertificateService certService;

    public TenantCertificateController(TenantsRepository tenantsRepo,
                                        CertificateService certService) {
        this.tenantsRepo = tenantsRepo;
        this.certService = certService;
    }

    /**
     * 分页查询证书列表（含 X.509 解析信息）。
     *
     * @param teamName  团队名
     * @param pageNum   1 基页码（默认 1）
     * @param pageSize  每页大小（默认 10）
     * @param searchKey alias 模糊搜索（可选）
     * @return general_message 包装的分页结果（list + bean.nums）
     */
    @GetMapping
    @RequirePerm(PermCode.TEAM_CERTIFICATE)
    public Object listCertificates(
            @PathVariable("team_name") String teamName,
            @RequestParam(value = "page_num", defaultValue = "1") int pageNum,
            @RequestParam(value = "page_size", defaultValue = "10") int pageSize,
            @RequestParam(value = "search_key", required = false) String searchKey) {
        Tenants tenant = requireTenant(teamName);
        Page<Map<String, Object>> page = certService.listCertificates(
                tenant.getTenantId(), searchKey, pageNum, pageSize);
        // 返回 general_message 格式：list + bean.nums
        return GeneralMessage.okWithExtras(
                Map.of("nums", page.getTotalElements()),
                page.getContent(),
                null);
    }

    /**
     * 上传（创建）证书。
     *
     * @param teamName 团队名
     * @param body     包含 alias / certificate / private_key / certificate_type 字段
     * @return general_message 包装的结果，bean 含 alias / id
     */
    @PostMapping
    @RequirePerm(PermCode.TEAM_CERTIFICATE)
    public Object uploadCertificate(
            @PathVariable("team_name") String teamName,
            @RequestBody Map<String, Object> body) {
        Tenants tenant = requireTenant(teamName);

        String alias = (String) body.get("alias");
        String certificate = (String) body.get("certificate");
        String privateKey = (String) body.get("private_key");
        String certificateType = (String) body.getOrDefault("certificate_type", "服务端证书");
        // region_name：前端通常随请求带来，否则从 tenant 取出（部分场景无 region 调用）
        String regionName = (String) body.getOrDefault("region_name", "");

        ServiceDomainCertificate saved = certService.addCertificate(
                regionName, tenant, alias, certificate, privateKey, certificateType);

        Map<String, Object> bean = Map.of(
                "alias", saved.getAlias(),
                "id", saved.getId()
        );
        return GeneralMessage.ok(bean);
    }

    private Tenants requireTenant(String teamName) {
        return tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404,
                        "team not found: " + teamName, "团队不存在"));
    }
}
