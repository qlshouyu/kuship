## Why
region 组件域续：组件探针读 `teams/{team}/apps/{serviceAlias}/probe`（`AppProbeView.get`）——纯 DB。有探针→probe.to_dict()，无探针→404"探针不存在"。
## What Changes
- 探针读（对齐 `AppProbeView.get`）：`GET .../probe?mode=`：third_party 或无 mode → 任意探针；否则按 mode 取探针。找到→`code=200 bean=probe.to_dict()`(15 字段)；未找到→`code=404 msg=get probe error msg_show=探针不存在，您可能并未设置检测探针`（body code=404，HTTP 200）。`msg_show=查询成功`(成功时)。
- **不包含**：探针增删改、第三方组件特殊状态。
## Capabilities
### New Capabilities
- `component-probe-read`: 组件探针读（probe.to_dict / 404）。
## Impact
- 代码：ServiceProbe 实体+repo；ComponentProbeService；ComponentProbeController。
- 数据：只读 service_probe/tenant_service；无 schema 变更。
- 鉴权：AppBaseView（登录态）。
- 验证：有/无探针两情形对 7070 逐字节一致。
