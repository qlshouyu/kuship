## Context
ListAppPodsView.get：region get_service_pods→bean{new_pods,old_pods}；foobar(pods)：每 pod→{pod_name,pod_status,manage_name="manager",container:[...]}；container=dict(容器名→{memory_limit,memory_usage} bytes 字符串)，跳过 "POD"，bytes/1024/1024 round2，usage_rate=usage*100/limit round2(limit 0→0)，主容器(k8s_component_name in key and 'default-tcpmesh' not in key)与首元素交换。result={new_pods,old_pods}；general_message(list=result)→data.list=result(dict)，bean 默认 {}。region path 同 status(region_tenant_name)。复用 RegionClient(mTLS)。

## Goals / Non-Goals
**Goals:** 实例列表读 + 内存换算/主容器置首 + 稳定字段 parity。
**Non-Goals:** POST 进入实例、日志、pod 详情。

## Decisions
### 决策 1：TenantServiceInfo 加 k8s_component_name；ComponentPodsService.listPods：region GET .../pods→parse bean.new_pods/old_pods→transform(跳过 POD、bytes/1024/1024 BigDecimal HALF_EVEN scale2、usage_rate、主容器置首)→{new_pods,old_pods}；bean 空→{}
### 决策 2：data.list 放 dict：GeneralMessage.message(200,success,操作成功).putExtra("list", map)(覆盖默认 [])；old_pods=null 保留 null
### 决策 3：内存数值用 double(512.0/2.16)；round 用 HALF_EVEN 对齐 Python round

## Risks / Trade-offs
- region 运行态依赖(需 running 探针)；memory_usage/usage_rate 活体易变→稳定字段 parity(memory_limit 稳定可比)。
- list 字段是 dict（少见）——已用 putExtra 覆盖默认列表。

## Migration Plan
无 schema 变更。校准：running 探针 8000 vs 7070 稳定字段一致。

## Open Questions
- 无。
