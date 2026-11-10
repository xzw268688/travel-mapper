## ADDED Requirements

### Requirement: Storage connection test

管理员 MUST 能在对象存储配置页使用当前表单值测试连接，未填写的字段 MUST 回退到已保存配置。测试 MUST NOT 修改已保存配置，MUST 执行 `HeadBucket` 连通性探测，并在 `healthcheck/` 前缀上传、确认存在、删除一个临时对象，以验证凭证、签名与读写权限。测试 MUST 返回成功标识、耗时与可读错误信息；错误信息 MUST NOT 包含 AccessKey 或 SecretKey。页面 MUST 提示阿里云 OSS 需要关闭 Path Style（使用虚拟托管风格）。

#### Scenario: Reachable bucket passes the test
- **WHEN** 管理员填写正确的 Endpoint、Region、Bucket 与密钥并点击测试
- **THEN** 系统返回成功、耗时，并已删除临时测试对象

#### Scenario: Test does not change saved configuration
- **WHEN** 管理员在表单中填写新配置并测试
- **THEN** 已保存的存储配置保持不变，现有媒体仍使用原存储

#### Scenario: Invalid credentials fail with a readable message
- **WHEN** 管理员填写错误的 AccessKey/SecretKey 并测试
- **THEN** 系统返回失败与可读错误，不暴露密钥内容

#### Scenario: Blank key fields use saved credentials
- **WHEN** 管理员只修改 Endpoint 或 Bucket、密钥留空并测试
- **THEN** 系统使用已保存的密钥进行测试

#### Scenario: Alibaba OSS uses virtual-hosted style
- **WHEN** 管理员测试阿里云 OSS 且 Path Style 关闭
- **THEN** 测试通过；页面提示 OSS 仅支持虚拟托管风格