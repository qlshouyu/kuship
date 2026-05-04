package cn.kuship.console.modules.appruntime.controller;

import cn.kuship.console.common.context.RequestContext;
import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.entity.ServiceGroup;
import cn.kuship.console.modules.application.entity.ServiceGroupRelation;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.ServiceGroupRelationRepository;
import cn.kuship.console.modules.application.repository.ServiceGroupRepository;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.appcreate.service.AppDeleteService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** {@code DELETE /teams/{team_name}/groupapp/{group_id}/delete}：应用整体删除。 */
@RestController
@RequestMapping("/console/teams/{team_name}/groupapp/{group_id}")
public class AppGroupDeleteController {

    private final ServiceGroupRepository groupRepo;
    private final ServiceGroupRelationRepository relationRepo;
    private final TenantServiceRepository serviceRepo;
    private final AppDeleteService deleteService;
    private final RequestContext requestContext;

    public AppGroupDeleteController(ServiceGroupRepository groupRepo,
                                      ServiceGroupRelationRepository relationRepo,
                                      TenantServiceRepository serviceRepo,
                                      AppDeleteService deleteService,
                                      RequestContext requestContext) {
        this.groupRepo = groupRepo;
        this.relationRepo = relationRepo;
        this.serviceRepo = serviceRepo;
        this.deleteService = deleteService;
        this.requestContext = requestContext;
    }

    @DeleteMapping(value = {"/delete", "/delete/"})
    @RequirePerm(PermCode.APP_OVERVIEW_PERMS)
    @Transactional
    public ApiResult deleteGroup(@PathVariable("team_name") String teamName,
                                    @PathVariable("group_id") Integer groupId) {
        Integer userId = requestContext.getUserId();
        if (userId == null) {
            throw new ServiceHandleException(401, "missing user context", "未认证或 token 失效");
        }
        ServiceGroup group = groupRepo.findById(groupId)
                .orElseThrow(() -> new ServiceHandleException(404, "group not found", "应用不存在"));
        List<ServiceGroupRelation> rels = relationRepo.findByGroupId(groupId);
        List<String> serviceIds = rels.stream().map(ServiceGroupRelation::getServiceId).toList();
        List<TenantService> services = serviceIds.isEmpty() ? List.of()
                : serviceRepo.findByServiceIdIn(serviceIds);

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
        if (failed.isEmpty()) {
            // 全部子组件成功后，删除 application 自身
            groupRepo.delete(group);
        }
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("group_id", groupId);
        bean.put("success", success);
        bean.put("failed", failed);
        return GeneralMessage.ok(bean);
    }
}
