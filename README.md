# 旅迹地图（TravelMapper）

基于 Vue 3 + Spring Boot 的个人旅游规划与足迹可视化记录系统：在世界地图上点亮去过的国家，管理行程、游记与花费。

## 功能概览

- **世界地图**：国家三态着色、悬浮计数、国家/城市抽屉；旅行状态按日期与内容自动推导，支持手动标记与恢复。
- **行程规划**：AI 生成与手动规划切换、草稿微调（天/景点增删改排、跨天移动）、保存、再生成与合并、行前清单与官方链接。
- **游记**：TipTap 图文/视频编辑、粘贴/拖拽上传与压缩、选中文本 AI 流式润色、可见性/分享/下架、足迹海报与二维码。
- **记账**：多币种与汇率、快捷录入与金额表达式、按日期/国家/城市/旅行汇总与图表。
- **账号与额度**：邮箱验证码注册/找回密码、JWT、TOTP 2FA 与恢复码、会话吊销、卡密兑换与额度流水。
- **后台管理**：用户、卡密、城市、内容与媒体审核、官方链接、反馈、举报、汇率、公告、操作/登录日志、监控与告警、LLM/搜索/存储/邮件配置。

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3 + TypeScript + Vite、Pinia、Vue Router、Element Plus、ECharts、TipTap、html2canvas |
| 后端 | Spring Boot 3.3 / JDK 17、MyBatis-Plus、Flyway、Redis、JWT、Spring Mail、AWS S3 SDK |
| 数据与存储 | MySQL 8.0、Redis 7、MinIO / S3 兼容对象存储 |
| 测试与交付 | JUnit / Testcontainers、Vitest、Playwright、GitHub Actions、Docker Compose |

## 目录结构

```
backend/     Spring Boot 后端（controller / service / repository / common）
frontend/    Vue 3 前端（views / api / stores / router / e2e）
docs/        架构对照、运维与本地开发文档
deploy/      生产编排、Nginx 与发布脚本参考
openspec/    当前生效规格与变更归档
scripts/     性能基准、存储与维护脚本
```

## 文档

- `TravelMapper SRS V1.0.md` — 需求规格
- `TravelMapper Design  V1.0.md` — 概要设计
- `TravelMapper API  V1.0.md` — 接口契约
- `TravelMapper DB  V1.0.md` — 数据库设计
- `docs/architecture/doc-code-map.md` — 文档与代码对照（含已知差异）
- `docs/ops/local-development.md` — 本地开发与测试
- `docs/ops/deployment.md` — 生产部署（2C2G 单机 Docker）
- `deploy/` — 生产编排、Nginx、发布脚本参考
- `openspec/` — 当前生效的规格与变更记录

## 快速开始

见 `docs/ops/local-development.md`。

## 当前状态

- 开发分支：`release/travelmapper-v1.0.0-rc.1`
- 需求契约以 `openspec/specs/` 为准，其次是归档 change 与四份 V1.0 文档
- CI 覆盖后端单元/集成测试、UTC 时区矩阵、前端类型检查/构建/体积预算，`CI gate` 为合并必需检查