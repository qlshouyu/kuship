# P3-o 组件探针读 — 7070 校准基线

## 接口
`GET /console/teams/{tenantName}/apps/{serviceAlias}/probe?mode=`
- rainbond: `AppProbeView.get` —— 纯 DB。third_party 或无 mode → get_service_probe(任意 first)；非 third_party 有 mode → get_service_probe_by_mode。
- 有探针 → `code=200 msg_show=查询成功 bean=probe.to_dict()`(service_probe 15 列：ID,service_id,probe_id,mode,scheme,path,port,cmd,http_header,initial_delay_second,period_second,timeout_second,failure_threshold,success_threshold,is_used(bool))。
- 无探针 → `code=404 msg=get probe error msg_show=探针不存在，您可能并未设置检测探针`（body code=404，HTTP 200）。

## 校准结果（8000 vs 7070，team=default，组件 grcabaaf + readiness 探针）
```
[有探针 mode=readiness] code=200  15 字段 to_dict  MATCH ✓
[有探针 无 mode]        code=200  (取任意 first)    MATCH ✓
[无该 mode 探针 liveness] code=404 探针不存在        MATCH ✓
```
- 探针经配方部署组件 + 加端口 + POST .../probe(mode/scheme/path/port/...必填 path 非空)。校准毕清理恢复原状(service_probe=0)。
- **defer**：探针增删改、第三方组件特殊状态。
