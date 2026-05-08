package cn.kuship.console.modules.misc.upgrade.controller;

import cn.kuship.console.common.response.SkipResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 复刻 rainbond {@code console/views/upgrade.py::UpgradeVersionLView / UpgradeVersionRView /
 * UpgradeVersionImagesView} 三个端点（路径对齐 {@code /console/update/versions*}）。
 *
 * <ul>
 *   <li>{@code GET /console/update/versions}                         返回版本号字符串数组（降序）</li>
 *   <li>{@code GET /console/update/versions/{version}}              返回该版本的 detail 对象</li>
 *   <li>{@code GET /console/update/versions/{version}/images}       返回该版本的 images 对象</li>
 * </ul>
 *
 * <p>数据源 URL 由 {@code kuship.upgrade.versions-url} 配置项控制，缺省读取
 * {@code VERSION_INFO_URL} 环境变量，最终回退到 rainbond 公共源 {@code https://get.rainbond.com/upgrade-versions.json}。
 * 上游不可达时统一返空数组 / 空对象 + 200，与 rainbond 行为一致，前端不会因升级页拉失败白屏。
 *
 * <p>响应不走 {@code general_message} 自动包装（{@link SkipResponseWrapper}），与前端 {@code fetchAllVersion}
 * / {@code fetchVersionDetails} / {@code fetchVersionData}（{@code kuship-ui/src/services/api.js}）契约对齐。
 */
@RestController
@RequestMapping("/console/update")
public class ConsoleUpgradeController {

    private static final Logger log = LoggerFactory.getLogger(ConsoleUpgradeController.class);

    private final RestClient restClient = RestClient.builder().build();
    private final String versionsUrl;

    public ConsoleUpgradeController(
            @Value("${kuship.upgrade.versions-url:${VERSION_INFO_URL:https://get.rainbond.com/upgrade-versions.json}}")
            String versionsUrl) {
        this.versionsUrl = versionsUrl;
        log.info("[upgrade-versions] source={}", versionsUrl);
    }

    @GetMapping(value = {"/versions", "/versions/"})
    @SkipResponseWrapper
    public List<String> versions() {
        List<Map<String, Object>> data = fetchData();
        if (data == null) {
            return List.of();
        }
        return data.stream()
                .map(item -> Objects.toString(item.get("version"), null))
                .filter(Objects::nonNull)
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    @GetMapping(value = {"/versions/{version}", "/versions/{version}/"})
    @SkipResponseWrapper
    public Map<String, Object> versionDetail(@PathVariable("version") String version) {
        return findVersionField(version, "detail");
    }

    @GetMapping(value = {"/versions/{version}/images", "/versions/{version}/images/"})
    @SkipResponseWrapper
    public Map<String, Object> versionImages(@PathVariable("version") String version) {
        return findVersionField(version, "images");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findVersionField(String version, String field) {
        List<Map<String, Object>> data = fetchData();
        if (data == null) {
            return Map.of();
        }
        for (Map<String, Object> item : data) {
            if (version.equals(Objects.toString(item.get("version"), null))) {
                Object value = item.get(field);
                if (value instanceof Map<?, ?> map) {
                    return (Map<String, Object>) map;
                }
                return Map.of();
            }
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchData() {
        try {
            return restClient.get()
                    .uri(versionsUrl)
                    .retrieve()
                    .body(List.class);
        } catch (Exception e) {
            log.warn("[upgrade-versions] fetch from {} failed: {}", versionsUrl, e.toString());
            return null;
        }
    }
}
