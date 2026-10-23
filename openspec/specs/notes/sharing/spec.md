# Notes Sharing Specification

## Purpose

定义游记分享令牌与只读分享页，确保对外只暴露白名单字段、图片使用短时签名 URL，并在撤销、过期、删除、下架或注销时统一失效。

## Requirements

### Requirement: Share token creation and reuse
创建分享 MUST 接受 `expireDays` 为 7（默认）、30 或 0（永久）；已有有效令牌 MUST 复用并返回 `created=false`；已过期或被撤销的令牌 MUST 新建；游记已下架 MUST 返回 `2024`；非本人 MUST 返回 `2004`。

#### Scenario: First share creates token
- **WHEN** 用户首次为游记创建分享
- **THEN** 返回新令牌、URL 与到期时间，`created=true`

#### Scenario: Valid token is reused
- **WHEN** 用户再次对同一游记创建分享且已有有效令牌
- **THEN** 返回原令牌，`created=false`，不重复创建

#### Scenario: Expired token is replaced
- **WHEN** 原令牌已过期或被撤销
- **THEN** 系统创建新令牌并返回 `created=true`

### Requirement: Share revocation
撤销分享 MUST 撤销该游记的全部令牌，包括已过期的历史令牌；接口 MUST 返回撤销数量。

#### Scenario: Revoke all tokens
- **WHEN** 用户撤销某游记的分享
- **THEN** 该游记所有令牌 `revoked=1`，旧链接全部失效

#### Scenario: Revoked token cannot be reused
- **WHEN** 用户使用已撤销令牌访问分享页
- **THEN** 返回 `2007`“分享已失效”

### Requirement: Read-only share page whitelist
分享页 MUST 无需登录即可访问，且只返回：标题、正文纯文本前 200 字摘要、封面签名 URL、最多 6 张图片签名 URL、国家名、城市名、创建日期、脱敏昵称。MUST NOT 返回正文全文、消费金额、`tripId`、旅行名称、用户 ID、邮箱或其他游记。

#### Scenario: Whitelist response
- **WHEN** 访问有效分享令牌
- **THEN** 响应仅包含白名单字段，摘要不超过 200 字，图片不超过 6 张

#### Scenario: Private fields are absent
- **WHEN** 检查分享页响应
- **THEN** 不包含 `tripId`、旅行名称、花费金额、用户 ID 或邮箱

#### Scenario: Nickname is masked
- **WHEN** 分享页返回昵称
- **THEN** 昵称按规则脱敏（如“小*”）

### Requirement: Share token validation and invalidation
令牌不存在、已撤销、已过期、游记已软删除、游记已下架或用户已注销时，分享页 MUST 统一返回 `2007`，页面展示“分享已失效”，不得泄露具体失效原因。

#### Scenario: Deleted note invalidates share
- **WHEN** 游记被用户删除后访问其分享链接
- **THEN** 返回 `2007`

#### Scenario: Takedown invalidates share
- **WHEN** 游记被管理员下架后访问分享链接
- **THEN** 返回 `2007`

#### Scenario: Deleted user invalidates share
- **WHEN** 作者注销账号后访问其分享链接
- **THEN** 返回 `2007`

### Requirement: Signed URL lifetime on share page
分享页图片签名有效期 MUST 为分享令牌剩余有效期与 3600 秒中的较小值；令牌永久有效时 MUST 不超过 3600 秒；不得返回长期有效的图片直链。

#### Scenario: Short-lived signature
- **WHEN** 分享令牌剩余 2 小时
- **THEN** 图片签名有效期不超过 3600 秒

#### Scenario: Permanent token still caps signature
- **WHEN** 分享令牌为永久有效
- **THEN** 图片签名有效期仍不超过 3600 秒

### Requirement: Takedown and restore token semantics
内容下架 MUST 立即撤销所有相关分享令牌；恢复内容 MUST NOT 恢复旧令牌，用户 MUST 重新创建分享后才能再次对外访问。

#### Scenario: Takedown revokes immediately
- **WHEN** 管理员下架一篇已分享游记
- **THEN** 所有分享令牌立即失效，旧链接返回 `2007`

#### Scenario: Restore does not revive links
- **WHEN** 管理员恢复已被下架的游记
- **THEN** 本人可继续编辑，但原分享链接仍返回 `2007`

#### Scenario: New share after restore works
- **WHEN** 恢复后用户重新创建分享
- **THEN** 新令牌可正常访问分享页
