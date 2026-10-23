# Prep Official-links Specification

## Purpose

为行前准备提供国家官方渠道入口：已配置国家展示管理员维护的具体链接，未配置国家回退到通用搜索，并限制每国链接数量与 URL 安全。

## Requirements

### Requirement: Official link retrieval
系统 MUST 按国家返回已启用的官方链接，按 `sortOrder` 排序，每国最多 3 条；每条链接 MUST 为 http/https 且长度 ≤500 字符；不合格链接 MUST 被丢弃而不是返回错误。

#### Scenario: Configured country returns links
- **WHEN** 用户查看已配置官方链接的国家
- **THEN** 返回 `fallback=false` 和最多 3 条合法链接

#### Scenario: Invalid stored URL is filtered
- **WHEN** 数据库中某条链接不是 http/https 或超过长度限制
- **THEN** 该条不返回，其余合法链接正常返回

#### Scenario: Links are sorted
- **WHEN** 某国配置了多条链接
- **THEN** 响应按 `sortOrder` 升序排列

### Requirement: Fallback search entry
未配置官方链接的国家 MUST 返回 `fallback=true`、空 `links` 和可用的 `searchUrl`（以国家名 + “签证 官方”为关键词）；前端 MUST 以“去搜索”按钮打开新窗口，并提示外链。

#### Scenario: Unconfigured country falls back
- **WHEN** 用户查看没有配置链接的国家
- **THEN** 返回 `fallback=true` 与 `searchUrl`，不报错

#### Scenario: Fallback opens externally
- **WHEN** 用户点击“去搜索”
- **THEN** 浏览器在新窗口打开搜索页，并提示“即将离开本站”

### Requirement: Admin official link maintenance
管理员 MUST 能按国家查询、新增、修改和删除官方链接；每国最多 3 条，超过 MUST 返回 `2026`；URL MUST 通过 http/https 与长度校验；后台接口 MUST 仅管理员可访问。

#### Scenario: Fourth link is rejected
- **WHEN** 管理员为已有 3 条链接的国家再新增一条
- **THEN** 系统返回 `2026`，不写入

#### Scenario: Invalid URL is rejected
- **WHEN** 管理员提交非 http/https 或超长的 URL
- **THEN** 系统返回 `2001`，不写入

#### Scenario: Normal user cannot maintain links
- **WHEN** 普通用户调用官方链接后台接口
- **THEN** 系统返回 `4002`
