## Context

见 `proposal.md` 的 Why。现状：

- 全局样式为深色外壳（`#0f1720` + 黄色强调），但认证页使用浅色卡片，两套视觉并存。
- 地图页在弹性的卡片内，图例固定在底部，看起来像后台面板。
- 产品名只出现在 `App.vue`、`index.html`、`AuthShell`、`PosterView`、邮件与文档中。
- Element Plus 默认浅色主题未做品牌覆盖。

## Goals / Non-Goals

**Goals**

- 一套浅色、清爽的旅行蓝视觉，令牌化，用户页面精致、后台页面一致。
- 地图首页全屏沉浸，同时保留降级路径。
- 中文名在用户可见处一致。

**Non-Goals**

- 不做暗色模式、不做图标素材、不改代码包名/仓库/数据库/环境变量。
- 不重做后台页面布局。
- 不改变地图三态语义与业务数据。

## Decisions

### 1. 浅色为主，品牌蓝 `#3b5bdb`

深色外壳与浅色认证卡混用是当前最明显的割裂。统一为浅色背景 + 白卡片，品牌蓝承担链接、主按钮与导航激活态；地图状态绿/黄/灰保持原值，避免品牌色与状态色混淆。

### 2. 设计令牌集中在 `styles.css`

```css
:root {
  --tm-brand: #3b5bdb;
  --tm-brand-strong: #2f49b0;
  --tm-brand-soft: #eef2ff;
  --tm-text: #1f2d3d;
  --tm-text-secondary: #5b6577;
  --tm-text-muted: #8b95a7;
  --tm-bg: #f4f6fb;
  --tm-surface: #ffffff;
  --tm-border: #e3e8f2;
  --tm-radius: 12px;
  --tm-radius-sm: 8px;
  --tm-shadow: 0 8px 24px rgba(31, 45, 61, 0.06);
  --tm-shadow-soft: 0 2px 8px rgba(31, 45, 61, 0.05);
  --tm-space: 16px;
}
```

同时覆盖 Element Plus 主色变量（`--el-color-primary` 及其浅色阶），让按钮、链接、选中态自动跟随品牌色，避免逐组件改色。

### 3. 地图页全屏化

`.map-page` 改为相对定位、高度 `calc(100vh - 56px)`；`chartEl` 绝对定位铺满；品牌栏与图例绝对定位悬浮，`pointer-events` 仅在可交互元素上启用，避免遮挡地图拖拽。降级列表沿用现有 `fallback-map` 结构。

### 4. 品牌名替换只动展示层

界面、邮件、分享页、海报与文档中的产品名替换为「旅迹地图」；`com.travelmapper`、`/api/*`、表名、`TRAVELMAPPER_*` 保持不变，避免部署与数据迁移风险。

### 5. 后台页面只继承令牌

后台页面结构不变，仅通过全局令牌与 Element Plus 变量获得一致的背景、卡片、边框与按钮颜色。

## Risks / Trade-offs

- **浅色化后地图对比度** → 未点亮国家仍用深灰板色，海洋用浅蓝灰渐变；上线前看截图确认。
- **悬浮层遮挡地图手势** → 只给按钮/标签设置 `pointer-events: auto`，容器设为 `none`。
- **文档批量替换可能误伤技术标识** → 只替换独立单词 `TravelMapper`，跳过 `com.travelmapper`、`TRAVELMAPPER_` 与文件名。
- **视觉改动覆盖面大** → 以截图 + 现有 E2E 验收，功能测试不改断言。

## Migration Plan

1. 前端与后端同一版本发布（仅邮件文案变化，不影响接口）。
2. 回滚：回退前端构建即可，无数据变更。

## Open Questions

无。暗色模式与 logo 素材留待后续 change。