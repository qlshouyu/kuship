package cn.kuship.console.modules.app.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.app.entity.ServiceProbe;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.repository.ServiceProbeRepository;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * 组件探针读（对齐 AppProbeView.get）。third_party 或无 mode → 任意探针；否则按 mode。
 * 无探针 → 404 "探针不存在"。
 */
@Service
public class ComponentProbeService {

    private final TenantServiceInfoRepository serviceRepository;
    private final ServiceProbeRepository probeRepository;

    public ComponentProbeService(TenantServiceInfoRepository serviceRepository,
                                 ServiceProbeRepository probeRepository) {
        this.serviceRepository = serviceRepository;
        this.probeRepository = probeRepository;
    }

    /** 找到→返回 to_dict；未找到→返回 null（控制器据此出 404 body）。 */
    public Map<String, Object> getProbe(String serviceAlias, String mode) {
        TenantServiceInfo service = serviceRepository.findByServiceAlias(serviceAlias)
                .orElseThrow(() -> ServiceHandleException.notFound("service not found", "组件不存在"));
        boolean thirdParty = "third_party".equals(service.getServiceSource());
        Optional<ServiceProbe> probe;
        if (thirdParty || mode == null || mode.isEmpty()) {
            probe = probeRepository.findFirstByServiceId(service.getServiceId());
        } else {
            probe = probeRepository.findFirstByServiceIdAndMode(service.getServiceId(), mode);
        }
        return probe.map(ServiceProbe::toDict).orElse(null);
    }
}
