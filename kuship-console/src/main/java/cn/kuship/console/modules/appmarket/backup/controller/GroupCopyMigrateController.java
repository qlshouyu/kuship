package cn.kuship.console.modules.appmarket.backup.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.util.UuidGenerator;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 整组应用复制 / 迁移（占位）。 */
@RestController
@RequestMapping("/console/teams/{team_name}/groupapp/{group_id}")
public class GroupCopyMigrateController {

    @PostMapping(value = {"/copy", "/copy/"})
    public ApiResult copy(@PathVariable("team_name") String teamName,
                            @PathVariable("group_id") Integer groupId,
                            @RequestBody Map<String, Object> body) {
        // MVP：占位返回 new_group_id；完整复制 留作 hardening
        return GeneralMessage.ok(Map.of(
                "source_group_id", groupId,
                "new_group_id", 0,
                "target_team_name", body.getOrDefault("target_team_name", teamName)));
    }

    @PostMapping(value = {"/migrate", "/migrate/"})
    public ApiResult migrate(@PathVariable("team_name") String teamName,
                                @PathVariable("group_id") Integer groupId,
                                @RequestBody Map<String, Object> body) {
        return GeneralMessage.ok(Map.of(
                "record_id", UuidGenerator.makeUuid(),
                "source_group_id", groupId,
                "target_region", body.get("target_region")));
    }

    @GetMapping(value = {"/migrate/record", "/migrate/record/"})
    public ApiResult migrateRecord(@PathVariable("team_name") String teamName,
                                       @PathVariable("group_id") Integer groupId) {
        return GeneralMessage.okList(List.of());
    }
}
