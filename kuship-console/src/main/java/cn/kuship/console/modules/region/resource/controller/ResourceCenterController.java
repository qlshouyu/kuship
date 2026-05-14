package cn.kuship.console.modules.region.resource.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.response.SkipResponseWrapper;
import cn.kuship.console.infrastructure.region.api.ResourceCenterOperations;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 资源中心端点：工作负载详情 / Pod 详情 / 事件 / Pod 日志（SSE 透传）。
 *
 * <p>对应 rainbond {@code views/team_resources.py}：
 * {@code ResourceCenterWorkloadDetailView} / {@code ResourceCenterPodDetailView} /
 * {@code ResourceCenterEventsView} / {@code ResourceCenterPodLogsView}。
 */
@RestController
@RequestMapping("/console/teams/{team_name}/regions/{region_name}/resource-center")
public class ResourceCenterController {

    private static final Logger log = LoggerFactory.getLogger(ResourceCenterController.class);

    private final ResourceCenterOperations ops;

    public ResourceCenterController(ResourceCenterOperations ops) {
        this.ops = ops;
    }

    /** GET /workloads/{resource}/{name} — 工作负载详情。 */
    @GetMapping(value = {"/workloads/{resource}/{name}", "/workloads/{resource}/{name}/"})
    public ApiResult getWorkloadDetail(@PathVariable("team_name") String teamName,
                                        @PathVariable("region_name") String regionName,
                                        @PathVariable("resource") String resource,
                                        @PathVariable("name") String name,
                                        HttpServletRequest request) {
        Map<String, String> params = collectQueryParams(request);
        Map<String, Object> bean = ops.getWorkloadDetail(regionName, teamName, resource, name, params);
        return GeneralMessage.ok(bean);
    }

    /** GET /pods/{pod_name} — Pod 详情。 */
    @GetMapping(value = {"/pods/{pod_name}", "/pods/{pod_name}/"})
    public ApiResult getPodDetail(@PathVariable("team_name") String teamName,
                                   @PathVariable("region_name") String regionName,
                                   @PathVariable("pod_name") String podName) {
        Map<String, Object> bean = ops.getPodDetail(regionName, teamName, podName);
        return GeneralMessage.ok(bean);
    }

    /** GET /events — 对象事件（带 query params）。 */
    @GetMapping(value = {"/events", "/events/"})
    public ApiResult getEvents(@PathVariable("team_name") String teamName,
                                @PathVariable("region_name") String regionName,
                                HttpServletRequest request) {
        Map<String, String> params = collectQueryParams(request);
        Map<String, Object> bean = ops.getEvents(regionName, teamName, params);
        return GeneralMessage.ok(bean);
    }

    /**
     * GET /pods/{pod_name}/logs — Pod 日志 SSE 透传。
     *
     * <p>对应 rainbond {@code ResourceCenterPodLogsView}：
     * 先发一帧心跳注释（{@code ": heartbeat\n\n"}），再逐 chunk 透传 region 日志流。
     */
    @GetMapping(value = {"/pods/{pod_name}/logs", "/pods/{pod_name}/logs/"})
    @SkipResponseWrapper
    public ResponseEntity<StreamingResponseBody> getPodLogs(
            @PathVariable("team_name") String teamName,
            @PathVariable("region_name") String regionName,
            @PathVariable("pod_name") String podName,
            HttpServletRequest request) {
        Map<String, String> params = collectQueryParams(request);
        log.info("[ResourceCenter] pod logs request team={} region={} pod={} params={}",
                teamName, regionName, podName, params);

        StreamingResponseBody body = outputStream -> {
            // 立即发心跳帧（让 EventSource 建立连接）
            outputStream.write(": heartbeat\n\n".getBytes());
            outputStream.flush();
            InputStream stream = null;
            int chunkCount = 0;
            try {
                stream = ops.getPodLogStream(regionName, teamName, podName, params);
                byte[] buf = new byte[4096];
                int read;
                while ((read = stream.read(buf)) != -1) {
                    outputStream.write(buf, 0, read);
                    outputStream.flush();
                    chunkCount++;
                }
                log.info("[ResourceCenter] pod logs stream completed team={} region={} pod={} chunks={}",
                        teamName, regionName, podName, chunkCount);
            } catch (Exception e) {
                log.warn("[ResourceCenter] pod logs stream failed team={} region={} pod={}: {}",
                        teamName, regionName, podName, e.getMessage());
            } finally {
                if (stream != null) {
                    try { stream.close(); } catch (Exception ignored) {}
                }
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .header("Content-Encoding", "identity")
                .body(body);
    }

    private static Map<String, String> collectQueryParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) params.put(k, v[0]);
        });
        return params;
    }
}
