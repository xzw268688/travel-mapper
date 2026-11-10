## MODIFIED Requirements

### Requirement: API documentation view

管理员 MUST 能在后台查看接口文档：按分组展示 HTTP 方法、路径与摘要，并支持搜索。该页面 MUST 通过需管理员鉴权的后台接口获取数据，MUST NOT 依赖公开 OpenAPI 文档。页面仅在生产环境之外提供打开 Swagger UI 的入口；生产环境 MUST 关闭 Swagger UI 与 OpenAPI 文档端点，其访问 MUST 返回 404，MUST NOT 向公网公开接口清单。

#### Scenario: Admin browses API list
- **WHEN** 管理员打开接口文档页
- **THEN** 页面按分组展示接口的方法、路径与摘要

#### Scenario: Search API paths
- **WHEN** 管理员输入路径关键字
- **THEN** 列表只保留匹配的接口

#### Scenario: Production does not expose public API docs
- **WHEN** 在生产环境请求 `/swagger-ui.html`、`/swagger-ui/**` 或 `/v3/api-docs/**`
- **THEN** 系统返回 404，不返回 Swagger UI 或 OpenAPI 文档

#### Scenario: Swagger entry is hidden in production
- **WHEN** 管理员在生产环境打开接口文档页
- **THEN** 页面正常展示基于后台接口生成的接口清单，且不展示可用的 Swagger UI 跳转入口