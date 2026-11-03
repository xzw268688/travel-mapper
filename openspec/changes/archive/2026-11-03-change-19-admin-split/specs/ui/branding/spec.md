## ADDED Requirements

### Requirement: Admin navigation split
后台入口 MUST 拆分为「管理」与「配置」两个菜单，且仅对 `ADMIN` 角色显示。「管理」MUST 包含用户、卡密、城市、官方链接、汇率与内容管理；「配置」MUST 包含 AI 与搜索、邮件 SMTP 与对象存储。两个菜单 MUST 复用现有后台页面与设计令牌。

#### Scenario: Admin sees both menus
- **WHEN** 管理员登录后查看顶栏
- **THEN** 同时显示「管理」与「配置」入口

#### Scenario: Config items open the right tab
- **WHEN** 管理员从「配置」选择邮件 SMTP
- **THEN** 打开配置中心并定位到邮件页签

#### Scenario: Normal users see no admin menus
- **WHEN** 普通用户登录
- **THEN** 顶栏不显示「管理」或「配置」入口