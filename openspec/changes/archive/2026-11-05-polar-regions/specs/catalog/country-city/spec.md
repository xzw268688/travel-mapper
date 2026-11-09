## ADDED Requirements

### Requirement: Polar region coverage
国家目录 MUST 包含南极洲与北极地区两个启用地区，使其可以出现在地图、国家抽屉、国家下拉、时间线、记账与 AI 国家攻略中：

- 南极洲 MUST 使用 ISO 代码 `AQ`，中文名“南极洲”，英文名 `Antarctica`，大洲 `ANTARCTICA`。
- 北极地区 MUST 使用保留码 `X3`，中文名“北极地区”，英文名 `Arctic`，大洲为空。

两者 MUST NOT 预置城市。规划行程与游记仍要求城市，管理员补充城市后方可使用；记账的城市可选，因此可直接使用这两个地区。新增极地地区 MUST NOT 修改任何现有国家、城市或业务数据。

#### Scenario: Polar regions are listed
- **WHEN** 前端请求国家列表
- **THEN** 响应包含 `AQ` 南极洲与 `X3` 北极地区，且均为启用状态

#### Scenario: Antarctica opens an empty drawer
- **WHEN** 用户打开南极洲抽屉且没有任何数据
- **THEN** 返回灰色状态、空汇总、空分组与 AI 国家攻略入口

#### Scenario: Arctic opens an empty drawer
- **WHEN** 用户打开北极地区抽屉且没有任何数据
- **THEN** 返回灰色状态、空汇总、空分组与 AI 国家攻略入口

#### Scenario: No cities are preconfigured
- **WHEN** 用户请求南极洲或北极地区的城市列表
- **THEN** 接口返回空数组，规划/游记页提示需要管理员补充城市

#### Scenario: Existing data stays unchanged
- **WHEN** 执行极地地区迁移
- **THEN** 现有国家、城市、行程、游记与消费不受影响