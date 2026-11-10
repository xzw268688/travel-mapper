## Purpose

定义攻略搜索的供应商无关契约与降级语义，使行程生成在不依赖单一搜索服务的前提下获取参考链接，并在搜索失败时继续返回行程。

## ADDED Requirements

### Requirement: Provider-neutral search contract
系统 MUST 通过 `SearchProvider` 契约接收国家、城市集合、天数和规范化偏好关键词，返回标题、URL 和来源域名；搜索服务商与密钥 MUST 由 `sys_config` 提供，不得出现在契约参数中。

#### Scenario: Query carries normalized inputs
- **WHEN** 行程生成触发攻略搜索
- **THEN** 查询包含国家代码、国家名、城市代码集合、城市名集合、天数和规范化偏好文本

#### Scenario: Provider is selected by configuration
- **WHEN** 管理员把 `search.provider` 从博查切换为 Tavily
- **THEN** 下一次搜索使用新的实现，调用方无需改动

### Requirement: Timeout and failure semantics
搜索实现 MUST 使用连接超时 3 秒、读取超时 5 秒；超时 MUST 抛出 `SearchTimeoutException`，其他失败 MUST 抛出 `SearchException`；实现 MUST NOT 直接向调用方抛出供应商原始异常。

#### Scenario: Slow search times out
- **WHEN** 搜索服务在 5 秒内没有返回
- **THEN** 实现抛出超时异常，行程生成按降级处理

#### Scenario: Provider error is normalized
- **WHEN** 搜索服务返回非 2xx 或响应无法解析
- **THEN** 实现抛出统一的搜索异常，不泄露供应商细节

### Requirement: Result sanitization
搜索实现 MUST 只返回标题、URL 和来源域名，不解析网页正文；调用方 MUST 对 URL 做 http/https 和长度校验、去重，并最多保留 3 条。

#### Scenario: Invalid URL is dropped
- **WHEN** 搜索结果中包含非 http/https 或超长的 URL
- **THEN** 该条结果被丢弃，其余有效结果保留

#### Scenario: Duplicate results are removed
- **WHEN** 多个搜索结果指向同一 URL
- **THEN** 只保留一条，最终 guides 不超过 3 条

### Requirement: Configuration and credential isolation
搜索实现 MUST 从配置中心读取服务商与密钥，密钥 MUST NOT 出现在接口签名、返回结果或日志中。

#### Scenario: Key is never logged
- **WHEN** 搜索请求成功或失败
- **THEN** 应用日志只记录供应商名称、耗时和结果数量，不记录密钥或完整请求头
