package cn.kuship.console.modules.application.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.infrastructure.region.api.ServiceVolumeOperations;
import cn.kuship.console.modules.account.perm.PermCode;
import cn.kuship.console.modules.account.perm.RequirePerm;
import cn.kuship.console.modules.application.dto.VolumeReq;
import cn.kuship.console.modules.application.entity.TenantService;
import cn.kuship.console.modules.application.entity.TenantServiceVolume;
import cn.kuship.console.modules.application.repository.TenantServiceRepository;
import cn.kuship.console.modules.application.repository.TenantServiceVolumeRepository;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** {@code .../volumes}：先 region 后本地。 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps/{service_alias}/volumes")
public class AppVolumeController {

    private final TenantServiceVolumeRepository volumeRepo;
    private final TenantServiceRepository serviceRepo;
    private final ServiceVolumeOperations volumeOperations;

    public AppVolumeController(TenantServiceVolumeRepository volumeRepo,
                                 TenantServiceRepository serviceRepo,
                                 ServiceVolumeOperations volumeOperations) {
        this.volumeRepo = volumeRepo;
        this.serviceRepo = serviceRepo;
        this.volumeOperations = volumeOperations;
    }

    @GetMapping(value = {"", "/"})
    @RequirePerm(PermCode.APP_OVERVIEW_DESCRIBE)
    public ApiResult list(@PathVariable("team_name") String teamName,
                            @PathVariable("service_alias") String serviceAlias) {
        TenantService s = requireService(serviceAlias);
        return GeneralMessage.okList(volumeRepo.findByServiceId(s.getServiceId()).stream()
                .map(this::serialize).toList());
    }

    @PostMapping(value = {"", "/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    @Transactional
    public ApiResult add(@PathVariable("team_name") String teamName,
                           @PathVariable("service_alias") String serviceAlias,
                           @RequestBody @Valid VolumeReq req) {
        TenantService s = requireService(serviceAlias);
        Map<String, Object> body = toBody(req);
        volumeOperations.addVolumes(s.getServiceRegion(), teamName, serviceAlias, body);
        TenantServiceVolume v = new TenantServiceVolume();
        v.setServiceId(s.getServiceId());
        v.setVolumeName(req.volumeName());
        v.setVolumeType(req.volumeType());
        v.setVolumePath(req.volumePath());
        v.setVolumeCapacity(req.volumeCapacity() != null ? req.volumeCapacity() : 0);
        v.setAccessMode(req.accessMode());
        v.setSharePolicy(req.sharePolicy());
        v.setCategory(req.category() != null ? req.category() : "");
        v.setHostPath("");
        v.setMode(req.mode());
        return GeneralMessage.ok(serialize(volumeRepo.save(v)));
    }

    @DeleteMapping(value = {"/{volume_id}", "/{volume_id}/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    @Transactional
    public ApiResult delete(@PathVariable("team_name") String teamName,
                              @PathVariable("service_alias") String serviceAlias,
                              @PathVariable("volume_id") Integer volumeId) {
        TenantService s = requireService(serviceAlias);
        TenantServiceVolume v = volumeRepo.findById(volumeId)
                .orElseThrow(() -> new ServiceHandleException(404, "volume not found", "存储卷不存在"));
        volumeOperations.deleteVolumes(s.getServiceRegion(), teamName, serviceAlias,
                Map.of("volume_name", v.getVolumeName()));
        volumeRepo.delete(v);
        return GeneralMessage.ok();
    }

    @PutMapping(value = {"/{volume_id}", "/{volume_id}/"})
    @RequirePerm(PermCode.APP_CREATE_PERMS)
    @Transactional
    public ApiResult update(@PathVariable("team_name") String teamName,
                              @PathVariable("service_alias") String serviceAlias,
                              @PathVariable("volume_id") Integer volumeId,
                              @RequestBody @Valid VolumeReq req) {
        TenantService s = requireService(serviceAlias);
        TenantServiceVolume v = volumeRepo.findById(volumeId)
                .orElseThrow(() -> new ServiceHandleException(404, "volume not found", "存储卷不存在"));
        volumeOperations.upgradeVolumes(s.getServiceRegion(), teamName, serviceAlias, toBody(req));
        if (req.volumeCapacity() != null) v.setVolumeCapacity(req.volumeCapacity());
        if (req.accessMode() != null) v.setAccessMode(req.accessMode());
        if (req.sharePolicy() != null) v.setSharePolicy(req.sharePolicy());
        return GeneralMessage.ok(serialize(volumeRepo.save(v)));
    }

    private TenantService requireService(String serviceAlias) {
        return serviceRepo.findAll().stream()
                .filter(s -> serviceAlias.equals(s.getServiceAlias()))
                .findFirst()
                .orElseThrow(() -> new ServiceHandleException(404, "service not found", "组件不存在"));
    }

    private Map<String, Object> toBody(VolumeReq req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("volume_name", req.volumeName());
        m.put("volume_type", req.volumeType());
        m.put("volume_path", req.volumePath());
        m.put("volume_capacity", req.volumeCapacity());
        m.put("access_mode", req.accessMode());
        m.put("category", req.category());
        m.put("share_policy", req.sharePolicy());
        m.put("mode", req.mode());
        return m;
    }

    private Map<String, Object> serialize(TenantServiceVolume v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("volume_id", v.getId());
        m.put("volume_name", v.getVolumeName());
        m.put("volume_type", v.getVolumeType());
        m.put("volume_path", v.getVolumePath());
        m.put("volume_capacity", v.getVolumeCapacity());
        m.put("access_mode", v.getAccessMode());
        m.put("share_policy", v.getSharePolicy());
        m.put("mode", v.getMode());
        return m;
    }
}
