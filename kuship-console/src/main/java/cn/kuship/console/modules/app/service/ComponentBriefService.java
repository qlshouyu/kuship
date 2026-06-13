package cn.kuship.console.modules.app.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 组件概览读（对齐 AppBriefView.get 非 market 路径）：bean = service.to_dict()（纯全列，不附加 group/disk_cap，
 * namespace 为 DB 原值）。market 校验分支 defer（msg 恒 "查询成功"）。
 */
@Service
public class ComponentBriefService {

    private final TenantServiceInfoRepository serviceRepository;

    public ComponentBriefService(TenantServiceInfoRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    public Map<String, Object> getBrief(String serviceAlias) {
        TenantServiceInfo service = serviceRepository.findByServiceAlias(serviceAlias)
                .orElseThrow(() -> ServiceHandleException.notFound("service not found", "组件不存在"));
        return service.toDict();
    }
}
