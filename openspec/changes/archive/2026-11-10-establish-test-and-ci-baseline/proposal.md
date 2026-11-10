## Why

后端已有约 268 个测试、前端已有 22 个 Playwright 用例，但没有任何自动化流程运行它们：`deploy/release.sh` 用 `-DskipTests` 打包，前端没有单元测试，仓库也没有 CI。结果是回归只能靠人工发现，而接下来要做的时区统一与前端性能优化都缺少可以依赖的门禁，改动没有安全网。

## What Changes

- 新增 GitHub Actions 工作流：
  - PR 门禁：后端单元测试 + Testcontainers 集成测试、前端 Vitest 单元测试、`vue-tsc` 类型检查与生产构建、gzip 体积预算检查。
  - 夜间与发布前：Playwright E2E 改为针对生产构建（`vite preview`）运行，由 CI 自动拉起 MySQL/Redis/S3 兼容服务并写入种子管理员。
- 引入 Vitest + Vue Test Utils + happy-dom，新增 `npm run test:unit`；首批覆盖 `authStorage`、`email`、`share`、`noteContent` 等纯工具、auth store、路由守卫与 `api/http` 拦截器。日期工具测试随后续时区 change 一起落地。
- 拆分 Maven 单元测试与集成测试执行方式（Surefire 排除 `*IntegrationTest`，Failsafe 在 `verify` 阶段执行），使 CI 能明确分别运行，保留本地文档中的外部容器模式。
- 新增体积预算脚本：基于 Vite manifest 与 gzip 实测，先按当前基线设阈值（入口 JS ≤ 950KB gzip、JS+CSS 合计 ≤ 1.1MB、首屏含 `world.json` ≤ 1.4MB），后续性能 change 再收紧到目标值。
- Playwright 配置支持生产构建模式，CI 使用 `vite preview`，本地默认仍可用 dev server。
- `deploy/release.sh` 在构建前校验当前 commit 的 GitHub Actions 状态，失败则终止发布。
- 更新 `docs/ops/local-development.md`，补充单元测试、集成测试拆分与 CI 触发说明。

## Capabilities

### New Capabilities

无。本 change 仅涉及构建、测试与发布流程，不改变任何 API、数据或用户可见行为，`.openspec.yaml` 设置 `skip_specs: true`。

### Modified Capabilities

无。

## Impact

- 新增：`.github/workflows/`、`frontend/vitest.config.ts`、`frontend/src/**/*.test.ts`、`frontend/scripts/check-bundle-budget.mjs`。
- 修改：`frontend/package.json`（依赖与脚本）、`frontend/package-lock.json`、`frontend/vite.config.ts`（manifest 与测试配置）、`frontend/playwright.config.ts`、`backend/pom.xml`（Surefire/Failsafe）、`deploy/release.sh`、`docs/ops/local-development.md`。
- 运行时不改动；无数据库、API 或前端行为变化。
- CI 依赖 GitHub Actions 托管 runner（Ubuntu，自带 Docker，供 Testcontainers 使用）；E2E 夜间任务需要可用的种子管理员配置。
- 发布流程新增对 CI 状态的依赖，首次上线前需要为 `main` 与 `release/*` 配置或人工确认分支保护。