## Why
region 组件域续：组件概览读 `teams/{team}/apps/{serviceAlias}/brief`（`AppBriefView.get`）——非 market 路径 bean=service.to_dict()，纯 DB 复用 P3-i 全列实体，秒级校准。

## What Changes
- 组件概览（对齐 `AppBriefView.get` 非 market 路径）：`GET .../brief?region_name=` → `bean`=`service.to_dict()`（63 列全字段，namespace 为 DB 原值、不附加 group_name/group_id/disk_cap），`msg_show=查询成功`。
- **不包含**：market 安装源校验分支（msg 可能为 MarketAppLost/RbdAppNotFound 提示）。

## Capabilities
### New Capabilities
- `component-brief-read`: 组件概览读（service.to_dict 全列，纯 DB）。

## Impact
- 代码：ComponentBriefService；ComponentBriefController（复用 TenantServiceInfo.toDict）。
- 数据：只读 tenant_service；无 schema 变更。
- 鉴权：AppBaseView（登录态）。
- 验证：纯 DB，bean 63 字段对 7070 逐字节一致。
