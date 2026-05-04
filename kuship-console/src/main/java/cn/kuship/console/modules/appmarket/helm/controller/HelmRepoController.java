package cn.kuship.console.modules.appmarket.helm.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.util.UuidGenerator;
import cn.kuship.console.modules.appmarket.helm.entity.HelmRepo;
import cn.kuship.console.modules.appmarket.helm.repository.HelmRepoRepository;
import cn.kuship.console.modules.appmarket.helm.util.AesGcmEncryptor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Helm Chart Repo CRUD（密码 AES-GCM 加密）。 */
@RestController
public class HelmRepoController {

    private final HelmRepoRepository repo;
    private final AesGcmEncryptor encryptor;

    public HelmRepoController(HelmRepoRepository repo, AesGcmEncryptor encryptor) {
        this.repo = repo;
        this.encryptor = encryptor;
    }

    @GetMapping(value = {"/console/helm/repos", "/console/helm/repos/"})
    public ApiResult list() {
        List<HelmRepo> all = repo.findAll();
        return GeneralMessage.okList(all.stream().map(HelmRepoController::toMaskedBean).toList());
    }

    @PostMapping(value = {"/console/helm/repos", "/console/helm/repos/"})
    @Transactional
    public ApiResult create(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        if (name == null || name.isBlank()) {
            throw new ServiceHandleException(400, "missing name", "缺少 name");
        }
        if (repo.findByRepoName(name).isPresent()) {
            throw new ServiceHandleException(400, "repo exists", "Repo 已存在");
        }
        HelmRepo r = new HelmRepo();
        r.setRepoId(UuidGenerator.makeUuid());
        r.setRepoName(name);
        r.setRepoUrl((String) body.getOrDefault("url", ""));
        r.setUsername((String) body.getOrDefault("username", ""));
        r.setPassword(encryptor.encrypt((String) body.getOrDefault("password", "")));
        repo.save(r);
        return GeneralMessage.ok(toMaskedBean(r));
    }

    @DeleteMapping(value = {"/console/helm/repos", "/console/helm/repos/"})
    @Transactional
    public ApiResult delete(@RequestParam("name") String name) {
        repo.findByRepoName(name).ifPresent(repo::delete);
        return GeneralMessage.ok();
    }

    static Map<String, Object> toMaskedBean(HelmRepo r) {
        Map<String, Object> bean = new LinkedHashMap<>();
        bean.put("repo_id", r.getRepoId());
        bean.put("name", r.getRepoName());
        bean.put("url", r.getRepoUrl());
        bean.put("username", r.getUsername());
        bean.put("password", r.getPassword() == null || r.getPassword().isEmpty() ? "" : "***");
        return bean;
    }
}
