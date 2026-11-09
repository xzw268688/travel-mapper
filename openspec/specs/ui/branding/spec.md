# Ui Branding Specification

## Purpose

定义面向用户的产品名称与视觉呈现：统一使用中文名「旅迹地图」，保留 TravelMapper 作为副标，并保证地图首页的沉浸式布局在底图不可用时仍能完整使用。

## Requirements

### Requirement: Product name consistency
面向用户的界面 MUST 显示产品名「旅迹地图」。`TravelMapper` MAY 作为小字副标出现，但 MUST NOT 单独作为主标题。浏览器标题、认证页、主导航、分享页、海报与系统邮件 MUST 使用同一中文名，MUST NOT 残留旧名作为主标识。代码标识符（包名、接口路径、数据库名、环境变量前缀）MUST NOT 因改名而变化。

#### Scenario: Browser title shows the Chinese name
- **WHEN** 用户打开任意页面
- **THEN** 浏览器标题以「旅迹地图」开头

#### Scenario: Emails use the Chinese name
- **WHEN** 系统发送验证码邮件
- **THEN** 邮件主题与正文显示「旅迹地图」，不把 TravelMapper 作为主标识

#### Scenario: Sharing surfaces use the Chinese name
- **WHEN** 用户查看分享页或生成海报
- **THEN** 页面与海报显示「旅迹地图」品牌名

### Requirement: Immersive map layout
地图首页 MUST 以全屏方式展示地图，品牌栏与图例 MUST 悬浮在地图之上且不遮挡国家点击。移动端 MUST 保留安全区与可点击图例。底图加载失败时 MUST 自动降级为国家列表，并保留进入国家详情的能力。

#### Scenario: Map fills the viewport
- **WHEN** 用户进入地图首页
- **THEN** 地图铺满可视区域，品牌栏和图例悬浮显示在地图上层

#### Scenario: Fallback keeps the product usable
- **WHEN** 世界地图底图加载失败
- **THEN** 页面显示告警与完整国家列表，点击国家仍可打开详情抽屉

### Requirement: Unified visual tokens
用户页面 MUST 使用同一套设计令牌定义品牌色、三级文字色、背景、卡片、边框、圆角与阴影；后台页面 MUST 复用同一令牌。地图的绿、黄、灰状态色 MUST 与品牌色保持可区分，MUST NOT 使用品牌色表达地图状态。

#### Scenario: Status colors stay distinguishable
- **WHEN** 用户查看地图图例与已点亮国家
- **THEN** 绿/黄/灰状态与品牌蓝在视觉上可区分，图例文案与颜色对应

#### Scenario: Admin pages reuse the same tokens
- **WHEN** 管理员打开后台页面
- **THEN** 页面背景、卡片、边框与用户页面一致，仅保留既有功能布局

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

### Requirement: Global back navigation
顶栏 MUST 提供「返回」按钮，用于返回浏览器历史中的上一页；按钮 MUST 在除首页、登录/注册页与公开分享页之外的路由显示。当没有可返回的历史记录时，点击 MUST 回到首页且 MUST NOT 报错或停留在当前页。返回操作 MUST NOT 改变任何业务数据。

#### Scenario: Back button appears on subpages
- **WHEN** 用户打开行程编辑、游记详情、海报或后台等子页面
- **THEN** 顶栏显示「返回」按钮

#### Scenario: Back returns to the previous page
- **WHEN** 用户从地图进入国家抽屉并打开行程编辑页后点击「返回」
- **THEN** 回到上一页（国家抽屉所在的地图页或来源页面）

#### Scenario: No history falls back to home
- **WHEN** 用户在无历史记录的新标签页直接打开子页面并点击「返回」
- **THEN** 跳转到首页，不报错

#### Scenario: Home and public pages hide the button
- **WHEN** 用户处于首页、登录/注册页或公开分享页
- **THEN** 顶栏不显示「返回」按钮
