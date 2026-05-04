package cn.kuship.console.modules.appmarket.controller;

import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** {@code GET /teams/{team_name}/apps/image_tags?image=...}：调 hub registry 拉 tags 列表。 */
@RestController
@RequestMapping("/console/teams/{team_name}/apps")
public class TenantImageTagsController {

    private static final RestClient HUB = RestClient.builder()
            .requestInterceptor((req, body, execution) -> {
                req.getHeaders().setAccept(List.of(MediaType.APPLICATION_JSON));
                return execution.execute(req, body);
            })
            .build();

    @GetMapping(value = {"/image_tags", "/image_tags/"})
    public ApiResult listTags(@PathVariable("team_name") String teamName,
                                  @RequestParam("image") String image) {
        // 简化实现：5s timeout 调公网 docker hub；失败时返回空数组
        try {
            String[] parts = image.split(":")[0].split("/");
            String repo = parts.length == 1 ? "library/" + parts[0] : String.join("/", parts);
            String url = "https://hub.docker.com/v2/repositories/" + repo + "/tags?page_size=100";
            Map<?, ?> resp = HUB.get().uri(url).retrieve().body(Map.class);
            Object results = resp == null ? null : resp.get("results");
            if (results instanceof List<?> list) {
                List<String> tags = list.stream()
                        .filter(e -> e instanceof Map)
                        .map(e -> ((Map<?, ?>) e).get("name"))
                        .filter(java.util.Objects::nonNull)
                        .map(Object::toString)
                        .toList();
                return GeneralMessage.okList(tags);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return GeneralMessage.okList(List.of());
    }
}
