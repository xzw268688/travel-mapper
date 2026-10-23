## Why

TravelMapper V1.0 的四份契约文档已封版，但仓库里只有文档，没有可执行的工程骨架。认证、行程、游记、记账等后续 change 都依赖同一套表结构、统一响应与错误码、配置热加载、对象存储和搜索抽象；先把这些一次性固化，后续 change 才能只写业务规则，避免重复定义基础设施或反复改表。

## What Changes

- 建立 MySQL 8.0 全部 19 张业务表（另含可选 `exchange_rate_history`），并落地初始化数据：`country`、`city`、`exchange_rate`、固定清单项、预置管理员、`sys_config` 默认配置。
- 固化 API 通用契约：统一响应包、错误码表、分页、`Idempotency-Key`、通用请求头、枚举值、`deleted`/`takedown` 过滤约定。
- 落地配置中心 `sys_config`：读写、AES 加密、页面掩码、修改后热加载并重建 LLM/Search/Storage Provider；提供管理员 LLM/OSS/搜索配置端点。
- 落地 `StorageProvider`（S3 兼容，云 OSS / MinIO），包含预签名上传/下载、对象 Key 规范、签名 URL 有效期、孤立对象每日清理。
- 落地 `SearchProvider` 契约与 `ProviderHolder`，只定义接口、超时/异常语义和配置读取方式；具体供应商在 change-04 接入。
- 落地 `audit_log`、限流、日志脱敏，以及数据库每日备份 + Redis AOF 的运维脚本与恢复步骤。
- **不包含**任何用户侧业务接口（认证、行程、游记、记账等），这些由 change-02 ~ change-05 交付。

## Capabilities

### New Capabilities

- `platform/persistence`: 19 张表的 DDL、初始数据、实体/Mapper、软删除与查询约定，作为所有业务 change 的唯一数据结构基线。
- `platform/config`: `sys_config` 配置中心、AES 加密与掩码、热加载、管理员 LLM/OSS/搜索配置端点。
- `platform/storage`: `StorageProvider` 抽象、OSS/MinIO 实现、预签名 URL、对象 Key 规范、孤立对象清理。
- `platform/search`: `SearchProvider` 契约、`ProviderHolder`、超时与异常语义（具体供应商后续接入）。

### Modified Capabilities

无。仓库当前没有任何已归档能力规格，本 change 全部为新增。

## Impact

- **新增代码**：数据库迁移/DDL、Spring Boot 基础工程、实体与 MyBatis-Plus Mapper、统一响应/异常/分页/幂等/限流组件、审计日志、配置中心、存储与搜索抽象、后台配置 Controller、备份脚本。
- **依赖**：MySQL 8.0、Redis 7、S3 兼容对象存储（生产云 OSS / 本地 MinIO）、SpringBoot 3 + Java 17、MyBatis-Plus、Lombok。
- **接口**：`GET/PUT /api/admin/config/llm`、`GET/PUT /api/admin/config/oss`、`GET/PUT /api/admin/config/search`（API 文档 12.1–12.4）。
- **后续依赖**：change-02 ~ change-05 全部依赖本 change 的表结构、统一响应契约、配置中心与存储/搜索抽象。
- **文档依据**：`TravelMapper DB V1.0.md` 全文；`TravelMapper API V1.0.md` 第 1、2 章、12.1–12.4；`TravelMapper Design V1.0.md` 3、4.1、4.2、7.8、8.3；`TravelMapper SRS V1.0.md` REQ-STO-001、REQ-SYS-002、7.5。
- **无破坏性变更**。
