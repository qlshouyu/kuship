package cn.kuship.console.modules.gateway.cert.controller;

import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.RequireEnterpriseAdmin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 企业级证书占位接口。
 *
 * <p>对齐 rainbond {@code urls.py:932} {@code CertificateView}：
 * <ul>
 *   <li>POST /console/enterprise/team/certificate</li>
 * </ul>
 *
 * <p>rainbond Python 实现仅占位返回 {@code {is_certificate: 1}}，本 change 同样占位以维持契约。
 * 企业级聚合视图待 enterprise 子 change 替换。
 */
@RestController
@RequestMapping({
        "/console/enterprise/team/certificate",
        "/console/enterprise/team/certificate/"
})
public class EnterpriseCertificateController {

    /**
     * 企业级证书查询占位。
     *
     * <p>与 rainbond {@code CertificateView.post} 行为完全一致，返回固定值 {@code is_certificate=1}。
     * 不调 region API，不查本地表，纯占位响应。
     *
     * @return {@code bean={is_certificate: 1}}
     */
    @PostMapping
    @RequireEnterpriseAdmin
    public Object enterpriseCertificateStub() {
        return GeneralMessage.ok(Map.of("is_certificate", 1));
    }
}
