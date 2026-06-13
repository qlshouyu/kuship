# P3-m 组件概览读 — 7070 校准基线

## 接口
`GET /console/teams/{tenantName}/apps/{serviceAlias}/brief?region_name=rainbond`
- rainbond: `AppBriefView.get` —— 非 market 路径 `bean = service.to_dict()`（纯全列，msg=查询成功）；market 路径 check_market_service_info 异常时改 msg（defer）。
- 与 P3-i detail 区别：brief **不附加** group_name/group_id/disk_cap，namespace 为 **DB 原值**（不覆盖为 tenant.namespace）。复用 P3-i 的 TenantServiceInfo.toDict()。

## 校准结果（8000 vs 7070，team=default，镜像组件 gr4104e7 未部署）
```
字段数: 63 / 63   顺序一致: True   msg_show: 查询成功(一致)
DIFF: MATCH ✓   (namespace=goodrain 为 DB 原值)
```
- 纯 DB，探针**仅创建**即可校准。校准毕删除组件+组，库恢复原状。
- **defer**：market 安装源校验分支（msg 变化）。
