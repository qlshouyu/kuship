package cn.kuship.console.modules.misc.audit.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.misc.audit.entity.OperationLog;
import cn.kuship.console.modules.misc.audit.repository.OperationLogRepository;
import cn.kuship.console.modules.misc.audit.repository.OperationLogSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** 操作审计日志查询：企业级 / 团队级 / 应用级。 */
@RestController
public class OperationLogController {

    private final OperationLogRepository repo;

    public OperationLogController(OperationLogRepository repo) {
        this.repo = repo;
    }

    @GetMapping(value = {"/console/enterprise/{enterprise_id}/operation-logs",
                          "/console/enterprise/{enterprise_id}/operation-logs/"})
    public ApiResult enterpriseList(@PathVariable("enterprise_id") String enterpriseId,
                                        @RequestParam(value = "page", defaultValue = "1") int page,
                                        @RequestParam(value = "page_size", defaultValue = "20") int pageSize) {
        Page<OperationLogSummary> result = repo.findEnterprisePage(enterpriseId,
                PageRequest.of(adjPage(page) - 1, adjSize(pageSize)));
        return wrap(result);
    }

    @GetMapping(value = {"/console/teams/{team_name}/operation-logs",
                          "/console/teams/{team_name}/operation-logs/"})
    public ApiResult teamList(@PathVariable("team_name") String teamName,
                                @RequestParam(value = "page", defaultValue = "1") int page,
                                @RequestParam(value = "page_size", defaultValue = "20") int pageSize) {
        Page<OperationLogSummary> result = repo.findTeamPage(teamName,
                PageRequest.of(adjPage(page) - 1, adjSize(pageSize)));
        return wrap(result);
    }

    @GetMapping(value = {"/console/teams/{team_name}/apps/{app_id}/operation-logs",
                          "/console/teams/{team_name}/apps/{app_id}/operation-logs/"})
    public ApiResult appList(@PathVariable("team_name") String teamName,
                                @PathVariable("app_id") Integer appId,
                                @RequestParam(value = "page", defaultValue = "1") int page,
                                @RequestParam(value = "page_size", defaultValue = "20") int pageSize) {
        Page<OperationLogSummary> result = repo.findAppPage(teamName, appId,
                PageRequest.of(adjPage(page) - 1, adjSize(pageSize)));
        return wrap(result);
    }

    @GetMapping(value = {"/console/operation-logs/{id}/detail",
                          "/console/operation-logs/{id}/detail/"})
    public ApiResult detail(@PathVariable("id") Integer id) {
        OperationLog log = repo.findById(id)
                .orElseThrow(() -> new ServiceHandleException(404, "log not found", "审计日志不存在"));
        Map<String, Object> bean = summaryToBean(new OperationLogSummary(
                log.getId(), log.getCreateTime(), log.getUsername(), log.getOperationType(),
                log.getEnterpriseId(), log.getTeamName(), log.getAppId(), log.getServiceAlias(),
                log.getComment(), log.getIsOpenapi(), log.getServiceCname(), log.getAppName(),
                log.getInformationType()));
        bean.put("old_information", log.getOldInformation());
        bean.put("new_information", log.getNewInformation());
        return GeneralMessage.ok(bean);
    }

    private static int adjPage(int p) { return p < 1 ? 1 : p; }
    private static int adjSize(int s) { return s < 1 || s > 200 ? 20 : s; }

    private static ApiResult wrap(Page<OperationLogSummary> result) {
        return GeneralMessage.ok(Map.of(
                "list", result.getContent().stream().map(OperationLogController::summaryToBean).toList(),
                "total", result.getTotalElements()));
    }

    static Map<String, Object> summaryToBean(OperationLogSummary s) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("id", s.id());
        b.put("create_time", s.createTime());
        b.put("username", s.username());
        b.put("operation_type", s.operationType());
        b.put("team_name", s.teamName());
        b.put("app_id", s.appId());
        b.put("service_alias", s.serviceAlias());
        b.put("service_cname", s.serviceCname());
        b.put("app_name", s.appName());
        b.put("comment", s.comment());
        b.put("is_openapi", s.isOpenapi());
        b.put("information_type", s.informationType());
        return b;
    }
}
