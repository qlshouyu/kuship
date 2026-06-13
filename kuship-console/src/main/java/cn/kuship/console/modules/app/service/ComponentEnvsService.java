package cn.kuship.console.modules.app.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.modules.app.entity.TenantServiceEnvVar;
import cn.kuship.console.modules.app.entity.TenantServiceInfo;
import cn.kuship.console.modules.app.repository.TenantServiceEnvVarRepository;
import cn.kuship.console.modules.app.repository.TenantServiceInfoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 组件环境变量列表读（对齐 AppEnvView.get，纯 DB 分页）。
 * env_type=inner/outer（必填），env_name 模糊（attr_name），page/page_size 分页，order by attr_name。
 */
@Service
public class ComponentEnvsService {

    private final TenantServiceInfoRepository serviceRepository;
    private final TenantServiceEnvVarRepository envRepository;

    public ComponentEnvsService(TenantServiceInfoRepository serviceRepository,
                                TenantServiceEnvVarRepository envRepository) {
        this.serviceRepository = serviceRepository;
        this.envRepository = envRepository;
    }

    /** 返回 {total, list}；env_type 非法 → 400 参数异常。 */
    public Result listEnvs(String tenantName, String serviceAlias, String envType, String envName, int page, int pageSize) {
        if (envType == null || (!"inner".equals(envType) && !"outer".equals(envType))) {
            throw new ServiceHandleException(400, "param error", "参数异常");
        }
        TenantServiceInfo service = serviceRepository.findByServiceAlias(serviceAlias)
                .orElseThrow(() -> ServiceHandleException.notFound("service not found", "组件不存在"));
        int p = Math.max(page, 1);
        int ps = Math.max(pageSize, 1);
        Pageable pageable = PageRequest.of(p - 1, ps);

        Page<TenantServiceEnvVar> result;
        if (envName != null && !envName.isEmpty()) {
            result = envRepository.findByTenantIdAndServiceIdAndScopeAndAttrNameContainingOrderByAttrName(
                    service.getTenantId(), service.getServiceId(), envType, envName, pageable);
        } else {
            result = envRepository.findByTenantIdAndServiceIdAndScopeOrderByAttrName(
                    service.getTenantId(), service.getServiceId(), envType, pageable);
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (TenantServiceEnvVar env : result.getContent()) {
            list.add(env.toEnvDict());
        }
        return new Result(result.getTotalElements(), list);
    }

    /** bean={total}, list=env_dicts。 */
    public record Result(long total, List<Map<String, Object>> list) {
    }
}
