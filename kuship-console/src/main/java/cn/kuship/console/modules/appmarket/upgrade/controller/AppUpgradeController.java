package cn.kuship.console.modules.appmarket.upgrade.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.util.UuidGenerator;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.appmarket.upgrade.entity.AppUpgradeRecord;
import cn.kuship.console.modules.appmarket.upgrade.repository.AppUpgradeRecordRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 应用整组升级生命周期。 */
@RestController
@RequestMapping("/console/teams/{team_name}/groups/{group_id}")
public class AppUpgradeController {

    private final AppUpgradeRecordRepository repo;
    private final TenantsRepository tenantsRepo;

    public AppUpgradeController(AppUpgradeRecordRepository repo, TenantsRepository tenantsRepo) {
        this.repo = repo;
        this.tenantsRepo = tenantsRepo;
    }

    @GetMapping(value = {"/upgrade-records", "/upgrade-records/"})
    public ApiResult list(@PathVariable("team_name") String teamName,
                            @PathVariable("group_id") Integer groupId) {
        return GeneralMessage.okList(repo.findByGroupIdOrderByCreateTimeDesc(groupId).stream()
                .map(AppUpgradeController::toBean).toList());
    }

    @PostMapping(value = {"/upgrade-records", "/upgrade-records/"})
    @Transactional
    public ApiResult create(@PathVariable("team_name") String teamName,
                              @PathVariable("group_id") Integer groupId,
                              @RequestBody Map<String, Object> body) {
        Tenants team = tenantsRepo.findByTenantName(teamName)
                .orElseThrow(() -> new ServiceHandleException(404, "team not found", "团队不存在"));
        AppUpgradeRecord r = new AppUpgradeRecord();
        r.setTenantId(team.getTenantId());
        r.setGroupId(groupId);
        r.setGroupKey(UuidGenerator.makeUuid());
        r.setGroupName(String.valueOf(body.getOrDefault("group_name", "")));
        r.setVersion(String.valueOf(body.getOrDefault("version", "")));
        r.setOldVersion(String.valueOf(body.getOrDefault("old_version", "")));
        r.setStatus(0);
        r.setMarketName((String) body.get("market_name"));
        r.setIsFromCloud(Boolean.TRUE.equals(body.get("is_from_cloud")));
        r.setUpgradeGroupId(body.get("upgrade_group_id") instanceof Number n ? n.intValue() : groupId);
        r.setRecordType("upgrade");
        r.setParentId(0);
        r.setCreateTime(LocalDateTime.now());
        r.setUpdateTime(LocalDateTime.now());
        repo.save(r);
        return GeneralMessage.ok(toBean(r));
    }

    @GetMapping(value = {"/upgrade-records/{record_id}", "/upgrade-records/{record_id}/"})
    public ApiResult detail(@PathVariable("team_name") String teamName,
                              @PathVariable("group_id") Integer groupId,
                              @PathVariable("record_id") Integer recordId) {
        AppUpgradeRecord r = repo.findById(recordId)
                .orElseThrow(() -> new ServiceHandleException(404, "record not found", "升级记录不存在"));
        return GeneralMessage.ok(toBean(r));
    }

    @PostMapping(value = {"/upgrade-records/{record_id}/upgrade",
                            "/upgrade-records/{record_id}/upgrade/"})
    @Transactional
    public ApiResult upgrade(@PathVariable("team_name") String teamName,
                                @PathVariable("group_id") Integer groupId,
                                @PathVariable("record_id") Integer recordId) {
        return updateStatus(recordId, 1);
    }

    @PostMapping(value = {"/upgrade-records/{record_id}/deploy",
                            "/upgrade-records/{record_id}/deploy/"})
    @Transactional
    public ApiResult deploy(@PathVariable("team_name") String teamName,
                              @PathVariable("group_id") Integer groupId,
                              @PathVariable("record_id") Integer recordId) {
        return updateStatus(recordId, 2);
    }

    @PostMapping(value = {"/upgrade-records/{record_id}/rollback",
                            "/upgrade-records/{record_id}/rollback/"})
    @Transactional
    public ApiResult rollback(@PathVariable("team_name") String teamName,
                                @PathVariable("group_id") Integer groupId,
                                @PathVariable("record_id") Integer recordId) {
        AppUpgradeRecord src = repo.findById(recordId)
                .orElseThrow(() -> new ServiceHandleException(404, "record not found", "升级记录不存在"));
        AppUpgradeRecord rb = new AppUpgradeRecord();
        rb.setTenantId(src.getTenantId());
        rb.setGroupId(src.getGroupId());
        rb.setGroupKey(src.getGroupKey());
        rb.setGroupName(src.getGroupName());
        rb.setVersion(src.getOldVersion());
        rb.setOldVersion(src.getVersion());
        rb.setStatus(1);
        rb.setMarketName(src.getMarketName());
        rb.setIsFromCloud(src.getIsFromCloud());
        rb.setUpgradeGroupId(src.getUpgradeGroupId());
        rb.setRecordType("rollback");
        rb.setParentId(src.getId());
        rb.setCreateTime(LocalDateTime.now());
        rb.setUpdateTime(LocalDateTime.now());
        repo.save(rb);
        return GeneralMessage.ok(toBean(rb));
    }

    @GetMapping(value = {"/upgrade-records/{record_id}/info", "/upgrade-records/{record_id}/info/"})
    public ApiResult info(@PathVariable("record_id") Integer recordId) {
        return detailById(recordId);
    }

    @GetMapping(value = {"/upgrade-records/{record_id}/detail", "/upgrade-records/{record_id}/detail/"})
    public ApiResult upgradeDetail(@PathVariable("record_id") Integer recordId) {
        return detailById(recordId);
    }

    @GetMapping(value = {"/upgrade-records/{record_id}/components",
                          "/upgrade-records/{record_id}/components/"})
    public ApiResult components(@PathVariable("record_id") Integer recordId) {
        return GeneralMessage.okList(List.of());
    }

    @GetMapping(value = {"/upgrade-version", "/upgrade-version/"})
    public ApiResult upgradeVersion(@PathVariable("team_name") String teamName,
                                       @PathVariable("group_id") Integer groupId) {
        // 占位：返回空版本
        return GeneralMessage.ok(Map.of("can_upgrade", false, "current_version", "", "latest_version", ""));
    }

    private ApiResult updateStatus(Integer recordId, int newStatus) {
        AppUpgradeRecord r = repo.findById(recordId)
                .orElseThrow(() -> new ServiceHandleException(404, "record not found", "升级记录不存在"));
        r.setStatus(newStatus);
        r.setUpdateTime(LocalDateTime.now());
        repo.save(r);
        return GeneralMessage.ok(toBean(r));
    }

    private ApiResult detailById(Integer recordId) {
        AppUpgradeRecord r = repo.findById(recordId)
                .orElseThrow(() -> new ServiceHandleException(404, "record not found", "升级记录不存在"));
        return GeneralMessage.ok(toBean(r));
    }

    static Map<String, Object> toBean(AppUpgradeRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("record_id", r.getId());
        m.put("group_id", r.getGroupId());
        m.put("group_key", r.getGroupKey());
        m.put("version", r.getVersion());
        m.put("old_version", r.getOldVersion());
        m.put("status", r.getStatus());
        m.put("record_type", r.getRecordType());
        m.put("parent_id", r.getParentId());
        m.put("market_name", r.getMarketName());
        m.put("is_from_cloud", r.getIsFromCloud());
        m.put("create_time", r.getCreateTime());
        m.put("update_time", r.getUpdateTime());
        return m;
    }
}
