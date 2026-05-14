package cn.kuship.console.modules.region.maven.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.modules.account.perm.RequireEnterpriseAdmin;
import cn.kuship.console.modules.region.maven.api.MavenSettingOperations;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** {@code /console/enterprise/{enterprise_id}/regions/{region_name}/mavensettings*}：maven 仓库配置 CRUD（透传）。 */
@RestController
@RequestMapping("/console/enterprise/{enterprise_id}/regions/{region_name}/mavensettings")
public class MavenSettingController {

    private final MavenSettingOperations mavenOps;

    public MavenSettingController(MavenSettingOperations mavenOps) {
        this.mavenOps = mavenOps;
    }

    @GetMapping
    @RequireEnterpriseAdmin
    public ApiResult listMavenSettings(@PathVariable("enterprise_id") String enterpriseId,
                                        @PathVariable("region_name") String regionName,
                                        @RequestParam(value = "onlyname", defaultValue = "true") String onlyName) {
        boolean projection = !"false".equalsIgnoreCase(onlyName);
        List<Map<String, Object>> list = mavenOps.listMavenSettings(enterpriseId, regionName, projection);
        return GeneralMessage.okList(list);
    }

    @PostMapping
    @RequireEnterpriseAdmin
    public ApiResult addMavenSetting(@PathVariable("enterprise_id") String enterpriseId,
                                      @PathVariable("region_name") String regionName,
                                      @RequestBody Map<String, Object> body) {
        Map<String, Object> bean = mavenOps.addMavenSetting(enterpriseId, regionName, body);
        return GeneralMessage.ok(bean);
    }

    @GetMapping("/{name}")
    @RequireEnterpriseAdmin
    public ApiResult getMavenSetting(@PathVariable("enterprise_id") String enterpriseId,
                                      @PathVariable("region_name") String regionName,
                                      @PathVariable("name") String name) {
        return GeneralMessage.ok(mavenOps.getMavenSetting(enterpriseId, regionName, name));
    }

    @PutMapping("/{name}")
    @RequireEnterpriseAdmin
    public ApiResult updateMavenSetting(@PathVariable("enterprise_id") String enterpriseId,
                                         @PathVariable("region_name") String regionName,
                                         @PathVariable("name") String name,
                                         @RequestBody Map<String, Object> body) {
        return GeneralMessage.ok(mavenOps.updateMavenSetting(enterpriseId, regionName, name, body));
    }

    @DeleteMapping("/{name}")
    @RequireEnterpriseAdmin
    public ApiResult deleteMavenSetting(@PathVariable("enterprise_id") String enterpriseId,
                                         @PathVariable("region_name") String regionName,
                                         @PathVariable("name") String name) {
        mavenOps.deleteMavenSetting(enterpriseId, regionName, name);
        return GeneralMessage.ok();
    }
}
