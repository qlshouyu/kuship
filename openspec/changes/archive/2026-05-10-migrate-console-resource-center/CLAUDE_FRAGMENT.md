### 资源中心（migrate-console-resource-center）

`cn.kuship.console.modules.region.resource` 落地"命名空间资源管理 / Helm Release 生命周期 / 资源中心工作负载/Pod/事件/日志" 共 20 个端点。

**子域结构**：
```
modules/region/resource/
├── api/ResourceCenterOperationsImpl.java   # @Primary 实现（委托 RegionClientFactory）
├── controller/
│   ├── NsResourceController.java          # NS 资源 CRUD（6 端点）
│   ├── TeamComponentsController.java      # 团队组件列表（1 端点，纯本地）
│   ├── HelmReleaseController.java         # Helm Release CRUD + 历史 + 回滚 + 预览（9 端点）
│   ├── ResourceCenterController.java      # 工作负载/Pod/事件/日志 SSE（4 端点）
│   └── ResourceCenterWsInfoController.java # WsInfo（1 端点）
├── entity/TeamHelmReleaseSource.java      # team_helm_release_source 表（16 列）
├── repository/TeamHelmReleaseSourceRepository.java
└── service/HelmReleaseSourceService.java  # enrich list/detail + saveOrUpdate + deleteByRelease
```

**新增 region API 接口**：`infrastructure/region/api/ResourceCenterOperations`（含 18 个 method）
+ `ResourceCenterOperationsDefaultImpl`（占位）+ `ResourceCenterOperationsImpl`（@Primary 实现）。

**辅助表 team_helm_release_source**：
- 记录每次 helm install/upgrade 的来源信息（source_type / repo_name / chart_name 等）
- 唯一约束 `(region_name, namespace, release_name)`
- 安装时 upsert，卸载时 delete；并发冲突由 catch DataIntegrityViolationException + retry 兜底

**store→repo 来源转换**（build_helm_install_body）：
- 对齐 rainbond Python `build_helm_install_body`
- `source_type=store` 时从 `helm_repo` 表查 `repo_url/username` 替换 body；`password` 不透传

**NS 资源 POST/PUT Content-Type 透传**：
- controller 接收 `@RequestBody byte[]`（`consumes = "*/*"`），读取 `request.getContentType()` 传给 ops
- 支持 `application/yaml` / `application/json` / 任意自定义类型

**Pod 日志 SSE**：
- `@SkipResponseWrapper` 跳过 GeneralMessageResponseBodyAdvice
- `StreamingResponseBody` 先发 `: heartbeat\n\n` 帧，再 loop 读 InputStream chunk 写出
- Content-Type: text/event-stream + Cache-Control: no-cache

**WsInfo event_websocket_url 逻辑**：
- 从 `region_info.wsurl` 取值，拼 `/event_log` 后缀
- wsurl 为空或 `auto` 时 fallback：`ws://<Host-header-host>:6060/event_log`

**测试**：
- `ResourceCenterIntegrationTest`（`@SpringBootTest + @MockitoBean ResourceCenterOperations + 真实 DB seed`）：验证 8 个端点 200 / source 持久化 / WsInfo 格式
- `HelmReleaseSourceServiceTest`（Mockito 单元测试）：验证 saveOrUpdate 去重逻辑 / legacy 默认 / listSourceInfoByReleases key 格式
