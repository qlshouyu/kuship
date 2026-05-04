package cn.kuship.console.modules.appmarket.market.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.appmarket.market.entity.CenterAppTag;
import cn.kuship.console.modules.appmarket.market.entity.CenterAppTagRelation;
import cn.kuship.console.modules.appmarket.market.repository.CenterAppTagRelationRepository;
import cn.kuship.console.modules.appmarket.market.repository.CenterAppTagRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 应用模板 Tag CRUD + 模板与 Tag 关联绑定/解绑。 */
@RestController
@RequestMapping("/console/enterprise/{enterprise_id}")
public class CenterAppTagController {

    private final CenterAppTagRepository tagRepo;
    private final CenterAppTagRelationRepository relRepo;

    public CenterAppTagController(CenterAppTagRepository tagRepo, CenterAppTagRelationRepository relRepo) {
        this.tagRepo = tagRepo;
        this.relRepo = relRepo;
    }

    @GetMapping(value = {"/app-models/tag", "/app-models/tag/"})
    public ApiResult list(@PathVariable("enterprise_id") String enterpriseId) {
        List<CenterAppTag> tags = tagRepo.findByEnterpriseIdAndIsDeletedFalse(enterpriseId);
        return GeneralMessage.okList(tags.stream().map(CenterAppTagController::toBean).toList());
    }

    @PostMapping(value = {"/app-models/tag", "/app-models/tag/"})
    @Transactional
    public ApiResult create(@PathVariable("enterprise_id") String enterpriseId,
                              @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        if (name == null || name.isBlank()) {
            throw new ServiceHandleException(400, "missing name", "缺少 name");
        }
        if (tagRepo.findByEnterpriseIdAndName(enterpriseId, name).isPresent()) {
            throw new ServiceHandleException(400, "tag exists", "Tag 已存在");
        }
        CenterAppTag tag = new CenterAppTag();
        tag.setName(name);
        tag.setEnterpriseId(enterpriseId);
        tag.setIsDeleted(false);
        tagRepo.save(tag);
        return GeneralMessage.ok(toBean(tag));
    }

    @PutMapping(value = {"/app-models/tag/{tag_id}", "/app-models/tag/{tag_id}/"})
    @Transactional
    public ApiResult update(@PathVariable("enterprise_id") String enterpriseId,
                              @PathVariable("tag_id") Integer tagId,
                              @RequestBody Map<String, Object> body) {
        CenterAppTag tag = tagRepo.findById(tagId)
                .orElseThrow(() -> new ServiceHandleException(404, "tag not found", "Tag 不存在"));
        if (body.get("name") instanceof String n) tag.setName(n);
        tagRepo.save(tag);
        return GeneralMessage.ok(toBean(tag));
    }

    @DeleteMapping(value = {"/app-models/tag/{tag_id}", "/app-models/tag/{tag_id}/"})
    @Transactional
    public ApiResult delete(@PathVariable("enterprise_id") String enterpriseId,
                              @PathVariable("tag_id") Integer tagId) {
        tagRepo.findById(tagId).ifPresent(t -> {
            t.setIsDeleted(true);
            tagRepo.save(t);
        });
        return GeneralMessage.ok();
    }

    @PostMapping(value = {"/app-model/{app_id}/tag", "/app-model/{app_id}/tag/"})
    @Transactional
    public ApiResult bind(@PathVariable("enterprise_id") String enterpriseId,
                            @PathVariable("app_id") String appId,
                            @RequestBody Map<String, Object> body) {
        Integer tagId = body.get("tag_id") instanceof Number n ? n.intValue() : null;
        if (tagId == null) {
            throw new ServiceHandleException(400, "missing tag_id", "缺少 tag_id");
        }
        // 防重复绑定
        boolean exists = relRepo.findByEnterpriseIdAndAppId(enterpriseId, appId).stream()
                .anyMatch(r -> r.getTagId().equals(tagId));
        if (!exists) {
            CenterAppTagRelation rel = new CenterAppTagRelation();
            rel.setEnterpriseId(enterpriseId);
            rel.setAppId(appId);
            rel.setTagId(tagId);
            relRepo.save(rel);
        }
        return GeneralMessage.ok();
    }

    @DeleteMapping(value = {"/app-model/{app_id}/tag", "/app-model/{app_id}/tag/"})
    @Transactional
    public ApiResult unbind(@PathVariable("enterprise_id") String enterpriseId,
                              @PathVariable("app_id") String appId,
                              @RequestBody Map<String, Object> body) {
        Integer tagId = body.get("tag_id") instanceof Number n ? n.intValue() : null;
        if (tagId == null) {
            throw new ServiceHandleException(400, "missing tag_id", "缺少 tag_id");
        }
        relRepo.deleteByAppIdAndTagId(appId, tagId);
        return GeneralMessage.ok();
    }

    static Map<String, Object> toBean(CenterAppTag t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tag_id", t.getId());
        m.put("name", t.getName());
        return m;
    }
}
