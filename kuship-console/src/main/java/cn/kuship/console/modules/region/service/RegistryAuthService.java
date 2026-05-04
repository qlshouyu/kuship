package cn.kuship.console.modules.region.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.util.UuidGenerator;
import cn.kuship.console.modules.region.dto.RegistryAuthReq;
import cn.kuship.console.modules.region.entity.TeamRegistryAuth;
import cn.kuship.console.modules.region.repository.TeamRegistryAuthRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Hub + Team 共享 registry 凭据 service。 */
@Service
public class RegistryAuthService {

    private final TeamRegistryAuthRepository repo;

    public RegistryAuthService(TeamRegistryAuthRepository repo) {
        this.repo = repo;
    }

    public List<TeamRegistryAuth> listHub(Integer userId) {
        return repo.findByTenantIdAndRegionNameAndUserId("", "", userId);
    }

    public List<TeamRegistryAuth> listTeam(String tenantId) {
        return repo.findByTenantId(tenantId);
    }

    public TeamRegistryAuth requireBySecret(String secretId) {
        return repo.findBySecretId(secretId)
                .orElseThrow(() -> new ServiceHandleException(404, "registry auth not found", "镜像仓库凭据不存在"));
    }

    @Transactional
    public TeamRegistryAuth create(String tenantId, Integer userId, RegistryAuthReq req) {
        String secretId = req.secretId() != null && !req.secretId().isBlank()
                ? req.secretId() : UuidGenerator.makeUuid();
        if (repo.findBySecretId(secretId).isPresent()) {
            throw new ServiceHandleException(400, "secret_id already exists", "凭据 ID 已存在");
        }
        TeamRegistryAuth auth = new TeamRegistryAuth();
        auth.setSecretId(secretId);
        auth.setTenantId(tenantId == null ? "" : tenantId);
        auth.setRegionName(req.regionName() == null ? "" : req.regionName());
        auth.setDomain(req.domain());
        auth.setUsername(req.username());
        auth.setPassword(req.password());
        auth.setHubType(req.hubType() == null ? "docker" : req.hubType());
        auth.setUserId(userId);
        auth.setCreateTime(LocalDateTime.now());
        auth.setUpdateTime(LocalDateTime.now());
        return repo.save(auth);
    }

    @Transactional
    public TeamRegistryAuth update(String secretId, RegistryAuthReq req) {
        TeamRegistryAuth auth = requireBySecret(secretId);
        if (req.username() != null) auth.setUsername(req.username());
        if (req.password() != null) auth.setPassword(req.password());
        if (req.hubType() != null) auth.setHubType(req.hubType());
        if (req.domain() != null) auth.setDomain(req.domain());
        auth.setUpdateTime(LocalDateTime.now());
        return repo.save(auth);
    }

    @Transactional
    public void delete(String secretId) {
        TeamRegistryAuth auth = requireBySecret(secretId);
        repo.delete(auth);
    }
}
