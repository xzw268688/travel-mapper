## MODIFIED Requirements

### Requirement: Itinerary deletion
删除行程 MUST 为软删除；MUST 返回删除后该国的 `countryStatusAfter`（`GREEN`/`YELLOW`/`GRAY`）；删除后旅行日期区间 MUST 重算。

若删除后旅行没有任何未删除行程、游记或消费，系统 MUST 在同一事务内软删除该旅行，并在响应中返回 `tripDeleted=true`；否则返回 `tripDeleted=false`。

#### Scenario: Last itinerary turns country gray
- **WHEN** 用户删除某国最后一条行程且该国没有游记
- **THEN** 返回 `countryStatusAfter=GRAY`，地图恢复暗灰

#### Scenario: Trip remains after last itinerary is deleted
- **WHEN** 某旅行下的最后一条行程被删除
- **THEN** 若旅行仍有游记或消费，则旅行保留且日期区间为空；若没有任何内容，则旅行被自动软删除

#### Scenario: Trip with remaining notes stays
- **WHEN** 用户删除一条行程，但旅行下仍有游记或消费
- **THEN** 旅行保留，响应返回 `tripDeleted=false`