package cn.kuship.console.modules.misc.audit.repository;

import java.time.LocalDateTime;

/** OperationLog 列表查询的轻量投影（不返 longtext old/new_information）。 */
public record OperationLogSummary(
        Integer id,
        LocalDateTime createTime,
        String username,
        String operationType,
        String enterpriseId,
        String teamName,
        Integer appId,
        String serviceAlias,
        String comment,
        Boolean isOpenapi,
        String serviceCname,
        String appName,
        String informationType) {}
