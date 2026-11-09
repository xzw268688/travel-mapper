## MODIFIED Requirements

### Requirement: Admin navigation split

后台入口 MUST 拆分为「管理」「配置」与「监控」三个菜单，且仅对 `ADMIN` 角色显示。「管理」MUST 包含用户、卡密、城市、内容管理、举报、反馈、汇率、通知公告与日志管理；「配置」MUST 包含 AI 与搜索、邮件 SMTP、对象存储与故障提醒；「监控」MUST 包含在线用户、服务监控、数据库监控、缓存监控、存储监控与接口文档。「官方链接维护」入口 MUST NOT 出现在菜单中，但路由、接口与用户端回退逻辑 MUST 保留。三个菜单 MUST 复用现有后台页面与设计令牌。日志管理页面 MUST 以「操作日志」与「登录日志」两个页签区分两类日志，并保留各自已有的筛选与分页能力；操作日志 MUST 提供操作人角色筛选并展示角色。

#### Scenario: Admin manages announcements
- **WHEN** 管理员从「管理」选择通知公告
- **THEN** 打开公告列表并可创建、发布或下线公告

#### Scenario: Admin sees both menus
- **WHEN** 管理员登录后查看顶栏
- **THEN** 同时显示「管理」「配置」与「监控」入口

#### Scenario: Monitoring opens the right page
- **WHEN** 管理员从「监控」选择缓存监控
- **THEN** 打开日志管理之外的缓存监控页并展示 Redis 概览

#### Scenario: Config items open the right tab
- **WHEN** 管理员从「配置」选择邮件 SMTP
- **THEN** 打开配置中心并定位到邮件页签

#### Scenario: Fault alert tab opens
- **WHEN** 管理员从「配置」选择故障提醒
- **THEN** 打开配置中心并定位到故障提醒页签

#### Scenario: Admin opens log management
- **WHEN** 管理员从「管理」选择「日志管理」
- **THEN** 打开日志页面并默认展示操作日志，可切换到登录日志

#### Scenario: Admin filters user operations
- **WHEN** 管理员在操作日志选择角色「用户」并查询
- **THEN** 页面只展示用户角色的操作记录，并显示每条记录的角色

#### Scenario: Old audit route still works
- **WHEN** 已收藏 `/admin/audit` 的管理员访问该地址
- **THEN** 页面重定向到日志管理的操作日志页签

#### Scenario: Audit log is reachable from the menu
- **WHEN** 管理员展开「管理」
- **THEN** 可以看到「日志管理」并进入对应页面

#### Scenario: Official links entry is hidden
- **WHEN** 管理员展开「管理」
- **THEN** 不显示「官方链接」入口，直接访问 `/admin/official-links` 仍可打开页面

#### Scenario: Normal users see no admin menus
- **WHEN** 普通用户登录
- **THEN** 顶栏不显示「管理」「配置」或「监控」入口