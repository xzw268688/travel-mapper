# Prep Checklist Specification

## Purpose

为每条已保存行程提供固定七项行前清单，按用户、国家与行程持久化勾选状态，跨设备保持一致且不消耗 AI 额度。

## Requirements

### Requirement: Fixed checklist definition
行前清单 MUST 固定为七项且顺序固定：`VISA`（签证已办）、`FLIGHT`（机票已订）、`HOTEL`（住宿已订）、`INSURANCE`（保险已买）、`CURRENCY`（货币已换）、`PLUG`（插头转换器已备）、`SIM`（电话卡/流量已办）；V1 MUST NOT 提供自定义清单项。

#### Scenario: All seven items are returned
- **WHEN** 用户打开任一已保存行程的清单
- **THEN** 返回七项固定枚举和中文标签，顺序不变

#### Scenario: No custom item endpoint
- **WHEN** 客户端尝试提交不在固定枚举内的 `itemKey`
- **THEN** 系统返回 `2022`，不写入

### Requirement: Checklist read
读取清单 MUST 校验行程属于当前用户；未落库的项 MUST 返回 `checked=false`；已落库的项 MUST 返回保存状态；结果 MUST 按“用户 + 国家 + 行程”隔离。

#### Scenario: Unchecked defaults are returned
- **WHEN** 用户首次打开某行程的清单
- **THEN** 七项全部返回且 `checked=false`

#### Scenario: Saved state is restored
- **WHEN** 用户勾选“签证已办”后刷新或换设备登录
- **THEN** 该项仍返回 `checked=true`

#### Scenario: Other user's checklist is hidden
- **WHEN** 用户 A 请求用户 B 的行程清单
- **THEN** 系统返回 `2003`

### Requirement: Checklist toggle
勾选/取消 MUST 使用 upsert 语义，以 `(user_id, itinerary_id, item_key)` 唯一；重复勾选或取消 MUST 保持幂等；操作 MUST NOT 消耗额度、MUST NOT 影响地图点亮。

#### Scenario: Toggle persists
- **WHEN** 用户勾选“机票已订”
- **THEN** 系统写入 `checked=true` 并返回最新 `updatedAt`

#### Scenario: Repeated toggle is idempotent
- **WHEN** 用户连续两次提交相同的 `checked` 值
- **THEN** 结果一致，不产生重复行

#### Scenario: Checklist does not affect quota or map
- **WHEN** 用户勾选任意清单项
- **THEN** AI 额度与地图状态均不变
