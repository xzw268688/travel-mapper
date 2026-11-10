## MODIFIED Requirements

### Requirement: Note creation
创建游记 MUST 校验：标题 1–50 字；正文 HTML UTF-8 字节 ≤200KB；图片为 `objectKey` 数组且 ≤20 张；`coverImage` MUST 属于 `images`；国家与城市必填且城市已启用；`visibility` 默认 `PRIVATE`。入库前 MUST 对标题和正文执行敏感词过滤并将命中词替换为 `***`，对正文执行 XSS 清洗，并生成纯文本摘要；标题过滤后若超过 50 字 MUST 截断为 50 字。

#### Scenario: Valid note is created and lights map green
- **WHEN** 用户提交合法游记
- **THEN** 游记保存成功，返回 `countryStatusAfter=GREEN`，默认仅本人可见

#### Scenario: Missing country or city is rejected
- **WHEN** 创建游记时未提供国家或城市
- **THEN** 系统返回 `2001` 或 `2002`，不保存

#### Scenario: Too many images are rejected
- **WHEN** 提交超过 20 张图片
- **THEN** 系统返回 `2018`，不保存

#### Scenario: Oversized content is rejected
- **WHEN** 正文 UTF-8 字节超过 200KB
- **THEN** 系统返回 `2020`，不保存

#### Scenario: Script tags are sanitized
- **WHEN** 正文包含 `<script>` 或其他可执行标记
- **THEN** 脚本被移除或转义，展示时不会执行

#### Scenario: Title sensitive words are filtered
- **WHEN** 标题包含配置的敏感词
- **THEN** 入库标题中的敏感词被替换为 `***`，且标题长度不超过 50 字

### Requirement: Takedown visibility for owner
游记被下架后，本人 MUST 仍可查看详情，但 MUST 看到 `takedown=true`、下架原因与下架时间，并被告知可通过「意见反馈」申诉；本人 MUST NOT 能编辑或创建分享；他人访问分享页 MUST 得到失效结果。下架原因为空时 MUST 返回 NULL。

#### Scenario: Owner sees takedown flag
- **WHEN** 用户查看自己被下架的游记
- **THEN** 详情可见且 `takedown=true`

#### Scenario: Owner sees takedown reason
- **WHEN** 管理员填写原因后下架游记，作者查看详情
- **THEN** 返回下架原因与下架时间

#### Scenario: Owner cannot re-share
- **WHEN** 用户对被下架游记调用创建分享
- **THEN** 系统返回 `2024`