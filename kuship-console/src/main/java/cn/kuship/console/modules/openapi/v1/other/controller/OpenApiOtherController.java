package cn.kuship.console.modules.openapi.v1.other.controller;

import cn.kuship.console.common.page.PageRequestAdapter;
import cn.kuship.console.modules.grayrelease.dto.GrayReleaseRecordDto;
import cn.kuship.console.modules.grayrelease.entity.GrayReleaseRecord;
import cn.kuship.console.modules.grayrelease.entity.GrayReleaseStatus;
import cn.kuship.console.modules.grayrelease.repository.GrayReleaseRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** OpenAPI v1 杂项端点（gateway / announcement / appstore / groupapp / config / gray-releases / mcp）= ~10 endpoint。 */
@RestController
public class OpenApiOtherController {

    private final GrayReleaseRecordRepository grayReleaseRepo;
    private final PageRequestAdapter pageAdapter;

    public OpenApiOtherController(GrayReleaseRecordRepository grayReleaseRepo,
                                     PageRequestAdapter pageAdapter) {
        this.grayReleaseRepo = grayReleaseRepo;
        this.pageAdapter = pageAdapter;
    }

    @GetMapping(value = {"/openapi/v1/httpdomains", "/openapi/v1/httpdomains/"})
    public List<Map<String, Object>> httpDomains() {
        return List.of();
    }

    @GetMapping(value = {"/openapi/v1/gray-releases", "/openapi/v1/gray-releases/"})
    public Map<String, Object> grayReleases(@RequestParam(value = "tenant_id", required = false) String tenantId,
                                                 @RequestParam(value = "status", required = false) String status,
                                                 @RequestParam(value = "page", required = false) Integer page,
                                                 @RequestParam(value = "page_size", required = false) Integer pageSize) {
        Pageable pageable = pageAdapter.toPageable(page, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        GrayReleaseStatus statusEnum = status == null || status.isBlank()
                ? null : GrayReleaseStatus.fromValue(status);
        Specification<GrayReleaseRecord> spec = (root, query, cb) -> {
            var preds = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (tenantId != null && !tenantId.isBlank()) {
                preds.add(cb.equal(root.get("tenantId"), tenantId));
            }
            if (statusEnum != null) {
                preds.add(cb.equal(root.get("status"), statusEnum));
            }
            return preds.isEmpty() ? null : cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Page<GrayReleaseRecord> records = grayReleaseRepo.findAll(spec, pageable);
        List<GrayReleaseRecordDto> dtos = records.getContent().stream().map(GrayReleaseRecordDto::from).toList();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("list", dtos);
        resp.put("total", records.getTotalElements());
        resp.put("page", pageable.getPageNumber() + 1);
        resp.put("page_size", pageable.getPageSize());
        return resp;
    }

    @PostMapping(value = {"/openapi/v1/mcp/query", "/openapi/v1/mcp/query/"})
    public Map<String, Object> mcpQuery(@RequestBody Map<String, Object> body) {
        // 透传第 11 阶段 MCP HTTP 入口的 stub
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", body.getOrDefault("jsonrpc", "2.0"));
        resp.put("id", body.get("id"));
        resp.put("result", Map.of("method", body.get("method"), "notice", "MCP via OpenAPI v1 stub"));
        return resp;
    }
}
