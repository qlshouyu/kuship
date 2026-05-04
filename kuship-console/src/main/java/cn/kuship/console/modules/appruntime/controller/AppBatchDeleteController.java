package cn.kuship.console.modules.appruntime.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.appcreate.service.AppDeleteService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** 批量删除 + again_delete 强制本地清理。 */
@RestController
public class AppBatchDeleteController {

    private final AppDeleteService deleteService;
    private final TenantServiceRepository serviceRepo;
    private final RequestContext requestContext;

    public AppBatchDeleteController(AppDeleteService deleteService,
                                      TenantServiceRepository serviceRepo,
                                      RequestContext requestContext) {
        this.deleteService = deleteService;
        this.serviceRepo = serviceRepo;
        this.requestContext = requestContext;
    }

    @DeleteMapping(value = {"/console/teams/{team_name}/batch_delete",
                              "/console/teams/{team_name}/batch_delete/"})
    @RequirePerm(PermCode.APP_OVERVIEW_PERMS)
    public ApiResult batchDelete(@PathVariable("team_name") String teamName,
                                    @RequestBody Map<String, Object> body) {
        Integer userId = requestContext.getUserId();
        if (userId == null) {
            throw new ServiceHandleException(401, "missing user context", "未认证或 token 失效");
        }
        Set<String> serviceIds = collectServiceIds(body);
        List<TenantService> services = serviceRepo.findByServiceIdIn(new ArrayList<>(serviceIds));
        List<String> success = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        for (TenantService s : services) {
            try {
                deleteService.delete(teamName, s.getServiceAlias(), userId);
                success.add(s.getServiceId());
            } catch (Exception e) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("service_id", s.getServiceId());
                f.put("msg", e.getMessage());
                failed.add(f);
            }
        }
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("success", success);
        bean.put("failed", failed);
        return GeneralMessage.ok(bean);
    }

    @DeleteMapping(value = {"/console/teams/{team_name}/again_delete",
                              "/console/teams/{team_name}/again_delete/"})
    @RequirePerm(PermCode.APP_OVERVIEW_PERMS)
    public ApiResult againDelete(@PathVariable("team_name") String teamName,
                                    @RequestBody Map<String, Object> body) {
        Integer userId = requestContext.getUserId();
        if (userId == null) {
            throw new ServiceHandleException(401, "missing user context", "未认证或 token 失效");
        }
        // 'again_delete' 用于 region 已不存在的脏数据清理：标记 third_party 跳过 region 调用
        Set<String> serviceIds = collectServiceIds(body);
        List<TenantService> services = serviceRepo.findByServiceIdIn(new ArrayList<>(serviceIds));
        List<String> success = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        for (TenantService s : services) {
            try {
                String origSrc = s.getServiceSource();
                s.setServiceSource("third_party");
                serviceRepo.save(s);
                deleteService.delete(teamName, s.getServiceAlias(), userId);
                success.add(s.getServiceId());
            } catch (Exception e) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("service_id", s.getServiceId());
                f.put("msg", e.getMessage());
                failed.add(f);
            }
        }
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("success", success);
        bean.put("failed", failed);
        return GeneralMessage.ok(bean);
    }

    @SuppressWarnings("unchecked")
    private Set<String> collectServiceIds(Map<String, Object> body) {
        Set<String> ids = new LinkedHashSet<>();
        Object sids = body.get("service_ids");
        if (sids instanceof List<?> l) l.forEach(o -> { if (o != null) ids.add(o.toString()); });
        return ids;
    }
}
