package cn.kuship.console.modules.appmarket.share.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.entity.RegionApp;
import cn.kuship.console.modules.application.entity.ServiceGroup;
import cn.kuship.console.modules.application.repository.RegionAppRepository;
import cn.kuship.console.modules.application.repository.ServiceGroupRepository;
import cn.kuship.console.modules.appmarket.share.api.ShareOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 应用发布历史列表（{@code GET /console/teams/{team_name}/groups/{group_id}/releases}）。
 *
 * <p>{@code group_id} 是 {@link ServiceGroup#getId()} (int PK)，controller 通过
 * {@link RegionAppRepository#findFirstByAppId(Integer)} 取 region_app_id；缺失时 200 + 空 list，
 * 不抛异常（与 rainbond Python 行为一致）。
 */
@RestController
public class AppReleasesController {

    private final ShareOperations shareOps;
    private final ServiceGroupRepository groupRepo;
    private final RegionAppRepository regionAppRepo;

    public AppReleasesController(ShareOperations shareOps,
                                   ServiceGroupRepository groupRepo,
                                   RegionAppRepository regionAppRepo) {
        this.shareOps = shareOps;
        this.groupRepo = groupRepo;
        this.regionAppRepo = regionAppRepo;
    }

    @GetMapping(value = {"/console/teams/{team_name}/groups/{group_id}/releases",
                          "/console/teams/{team_name}/groups/{group_id}/releases/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult releases(@PathVariable("team_name") String teamName,
                                @PathVariable("group_id") String groupId) {
        Integer groupIdInt;
        try {
            groupIdInt = Integer.parseInt(groupId);
        } catch (NumberFormatException ex) {
            throw new ServiceHandleException(400, "invalid group_id", "应用 ID 不合法");
        }
        ServiceGroup group = groupRepo.findById(groupIdInt)
                .orElseThrow(() -> new ServiceHandleException(404, "group not found", "应用不存在"));

        RegionApp regionApp = regionAppRepo.findFirstByAppId(groupIdInt).orElse(null);
        if (regionApp == null || regionApp.getRegionAppId() == null || regionApp.getRegionAppId().isBlank()) {
            return GeneralMessage.okList(List.of());
        }

        List<Object> list = shareOps.listAppReleases(group.getRegionName(), teamName, regionApp.getRegionAppId());
        return GeneralMessage.okList(list);
    }
}
