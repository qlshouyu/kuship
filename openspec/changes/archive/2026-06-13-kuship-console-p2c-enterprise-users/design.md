## Context
企业用户列表 `EnterPriseUsersCLView.get` → `get_user_by_eid(eid,name,page,size)` = `Users.filter(enterprise_id)`（按 user_id 升序）+ name 模糊(nick/real/phone/email) + 分页。项含 default_favorite（收藏表，测试用户均 null）。create_time 经 DRF 默认序列化为 ISO（带微秒）。纯 console 库。

## Goals / Non-Goals
**Goals:** 企业用户列表读 + 对 7070 校准（分页/query/create_time 精度/real_name 回退）。
**Non-Goals:** 用户写、收藏域、admin 角色、region 列表。

## Decisions
### 决策 1：modules/enterprise 下新增列表服务 + controller，复用 UserInfoRepository.findByEnterpriseId
### 决策 2：create_time 用原始 LocalDateTime（Jackson 默认 ISO），对齐 DRF ISO 微秒——与 P1-g tenant to_dict 的空格格式刻意不同（两端序列化口径不同，分别处理）；diff 校准微秒精度，若不符再显式格式化
### 决策 3：default_favorite_* 恒 null（无收藏域；两测试用户实测 null，校准通过）；real_name null→nick_name；排序 user_id 升序；分页/query 服务层切片
### 决策 4：JWTAuthApiView——仅登录鉴权，无 @RequiresPerms

## Risks / Trade-offs
- **[create_time 微秒精度]** Jackson 默认 LocalDateTime ISO 与 DRF 是否逐字符一致存疑 → diff interop(.908434 固定值)校准；不符则加 formatter。
- **[default_favorite 简化]** 恒 null，待收藏域补；当前两用户实测 null，一致。

## Migration Plan
无 schema 变更，纯只读。校准：interop/viewer 真实数据 deep-diff + query/分页。

## Open Questions
- create_time 微秒精度逐字符是否一致——diff 验证（决策 2）。
