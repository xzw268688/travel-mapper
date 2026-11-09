## ADDED Requirements

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