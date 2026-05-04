package cn.kuship.console.infrastructure.region.api.dto;

import java.util.List;

public record NamespaceListResp(
        List<String> namespaces) {
}
