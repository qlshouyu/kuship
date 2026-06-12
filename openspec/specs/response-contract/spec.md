# response-contract Specification

## Purpose

TBD - created by archiving change kuship-console-p0-contract-auth. Update Purpose after archive.

## Requirements

### Requirement: 响应信封自动包装

系统 SHALL 通过 `ResponseBodyAdvice` 自动把 controller 返回值包装成统一信封 `{code, msg, msg_show, data:{bean, list}}`，字段顺序固定为 `code → msg → msg_show → data`，`msg_show` MUST 以 snake_case 序列化（`@JsonProperty("msg_show")`）。`data` MUST 恒含 `bean`（默认 `{}`）与 `list`（默认 `[]`）。

#### Scenario: 返回 POJO/Map 包进 bean

- **WHEN** controller 返回一个 POJO 或 Map
- **THEN** 响应体为 `{code, msg, msg_show, data:{bean:<该对象>, list:[]}}`

#### Scenario: 返回 List 包进 list

- **WHEN** controller 返回一个 `List`
- **THEN** 响应体 `data.list` 为该列表，`data.bean` 为 `{}`

#### Scenario: 返回 Page 拆分 list 与 total

- **WHEN** controller 返回一个分页 `Page`
- **THEN** `data.list` 为 `content`，`data.total` 为总数（与 rainbond-console `general_message(..., total=total)` 一致，total 在 `data` 顶层）

#### Scenario: ApiResult 幂等不重复包装

- **WHEN** controller 已返回一个完整 `ApiResult` 信封对象
- **THEN** 系统不再二次包装，原样输出

#### Scenario: 跳过包装的端点

- **WHEN** 端点标注 `@SkipResponseWrapper`（如 SSE / 文件下载）
- **THEN** 系统不包装，原始响应直出

### Requirement: 全局异常映射与错误码

系统 SHALL 通过全局异常处理把异常映射为信封响应，且 HTTP 状态码 MUST 等于业务 `code`，与 rainbond-console 的 DRF 语义一致。

#### Scenario: 业务异常透传状态码

- **WHEN** 业务抛出 `ServiceHandleException`（携带 `status_code` 与中文 `msg_show`）
- **THEN** HTTP 状态码与 `code` 取自该异常，`msg_show` 为其中文文案

#### Scenario: 参数校验失败返回 400

- **WHEN** 请求触发参数校验异常（`MethodArgumentNotValid`/`ConstraintViolation` 等）
- **THEN** HTTP 状态码与 `code` 为 400

#### Scenario: 兜底异常返回 500 且带 trace_id

- **WHEN** 发生未被识别的 `Exception`
- **THEN** HTTP 状态码与 `code` 为 500，`data.bean.trace_id` 含本次请求的 TraceId
