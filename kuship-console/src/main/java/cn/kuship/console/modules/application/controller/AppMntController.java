package cn.kuship.console.modules.application.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.application.service.AppMntService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 组件存储挂载端点。
 *
 * <p>路由与 rainbond Python 端 {@code AppMntView} / {@code AppMntManageView} 对齐：
 * <ul>
 *   <li>{@code GET  /console/teams/{team_name}/apps/{service_alias}/mnt?type=mnt|unmnt}</li>
 *   <li>{@code POST /console/teams/{team_name}/apps/{service_alias}/mnt}</li>
 *   <li>{@code DELETE /console/teams/{team_name}/apps/{service_alias}/mnt/{dep_vol_id}}</li>
 * </ul>
 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps/{service_alias}/mnt")
public class AppMntController {

    private final AppMntService mntService;
    private final TenantServiceRepository serviceRepo;
    private final TenantsRepository tenantsRepo;

    public AppMntController(AppMntService mntService,
                              TenantServiceRepository serviceRepo,
                              TenantsRepository tenantsRepo) {
        this.mntService = mntService;
        this.serviceRepo = serviceRepo;
        this.tenantsRepo = tenantsRepo;
    }

    /**
     * 获取挂载列表（已挂载 / 可挂载）。
     *
     * <p>Python 锚点：{@code AppMntView.get}
     * {@code ?type=mnt}（默认）→ 已挂载；{@code ?type=unmnt} → 可挂载未挂载。
     */
    @GetMapping(value = {"", "/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult list(@PathVariable("team_name") String teamName,
                           @PathVariable("service_alias") String serviceAlias,
                           @RequestParam(defaultValue = "mnt") String type,
                           @RequestParam(defaultValue = "") String dep_app_group,
                           @RequestParam(defaultValue = "") String config_name) {
        Tenants team = requireTeam(teamName);
        TenantService service = requireService(serviceAlias);

        List<Map<String, Object>> result;
        if ("mnt".equals(type)) {
            result = mntService.getMounted(team.getTenantId(), service.getServiceId());
        } else if ("unmnt".equals(type)) {
            result = mntService.getUnmounted(team.getTenantId(), service.getServiceId(),
                    service.getServiceRegion(), dep_app_group, config_name);
        } else {
            throw new ServiceHandleException(400, "invalid type param", "参数 type 只接受 mnt 或 unmnt");
        }
        return GeneralMessage.okList(result);
    }

    /**
     * 批量挂载依赖存储。
     *
     * <p>Python 锚点：{@code AppMntView.post}
     * body 须含 {@code body} key，值为 JSON 数组字符串：
     * {@code [{"id": 49, "path": "/add"}, ...]}
     */
    @PostMapping(value = {"", "/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    @Transactional
    public ApiResult add(@PathVariable("team_name") String teamName,
                          @PathVariable("service_alias") String serviceAlias,
                          @RequestBody Map<String, Object> body) {
        Tenants team = requireTeam(teamName);
        TenantService service = requireService(serviceAlias);

        Object rawBody = body.get("body");
        if (rawBody == null) {
            throw new ServiceHandleException(400, "missing body field", "请求体须含 body 字段（挂载信息数组）");
        }

        List<Map<String, Object>> depVolList;
        try {
            //noinspection unchecked
            if (rawBody instanceof List) {
                depVolList = (List<Map<String, Object>>) rawBody;
            } else {
                // body 字段为 JSON 字符串时（rainbond UI 老版本传字符串）
                throw new ServiceHandleException(400, "body field must be a JSON array", "body 字段必须是数组");
            }
        } catch (ClassCastException e) {
            throw new ServiceHandleException(400, "body field format error", "body 字段格式错误");
        }

        if (depVolList.isEmpty()) {
            throw new ServiceHandleException(400, "empty mount list", "挂载列表不能为空");
        }

        for (Map<String, Object> depVol : depVolList) {
            Object idObj = depVol.get("id");
            Object pathObj = depVol.get("path");
            if (idObj == null || pathObj == null) {
                throw new ServiceHandleException(400, "missing id or path in mount item",
                        "挂载项须含 id 和 path 字段");
            }
            Integer depVolId;
            try {
                depVolId = idObj instanceof Integer ? (Integer) idObj : Integer.parseInt(idObj.toString());
            } catch (NumberFormatException e) {
                throw new ServiceHandleException(400, "invalid id format", "id 必须是整数");
            }
            String localPath = pathObj.toString();
            mntService.addMnt(service, teamName, team.getTenantId(), depVolId, localPath);
        }

        return GeneralMessage.ok();
    }

    /**
     * 取消挂载单个依赖存储。
     *
     * <p>Python 锚点：{@code AppMntManageView.delete}
     */
    @DeleteMapping(value = {"/{dep_vol_id}", "/{dep_vol_id}/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    @Transactional
    public ApiResult delete(@PathVariable("team_name") String teamName,
                              @PathVariable("service_alias") String serviceAlias,
                              @PathVariable("dep_vol_id") Integer depVolId) {
        Tenants team = requireTeam(teamName);
        TenantService service = requireService(serviceAlias);
        mntService.deleteMnt(service, teamName, team.getTenantId(), depVolId);
        return GeneralMessage.ok();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 内部工具
    // ─────────────────────────────────────────────────────────────────────────

    private Tenants requireTeam(String teamName) {
        return tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
    }

    private TenantService requireService(String serviceAlias) {
        return serviceRepo.findAll().stream()
                .filter(s -> serviceAlias.equals(s.getServiceAlias()))
                .findFirst()
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
    }
}
