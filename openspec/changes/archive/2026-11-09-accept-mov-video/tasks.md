## 1. 后端

- [x] 1.1 `UploadController.VIDEO_TYPES` 加入 `video/quicktime`，验证方式：上传签名接口接受 MOV
- [x] 1.2 `ObjectKeyGenerator` 将 `video/quicktime` 映射为 `mov`，验证方式：`ObjectKeyGeneratorTest` 断言 `.mov` 结尾
- [x] 1.3 新增上传签名集成测试：MOV 返回 `.mov` key，未知视频类型仍返回 `2030`，验证方式：测试通过

## 2. 前端

- [x] 2.1 `RichTextEditor.vue` 的视频 `accept` 与类型校验加入 `video/quicktime` / `.mov`，错误文案更新
- [x] 2.2 `NotesView.vue` 视频说明加入 MOV/HEVC 跨设备播放限制提示
- [x] 2.3 `npm run build` 通过

## 3. 文档与回归

- [x] 3.1 `TravelMapper API  V1.0.md` 上传接口视频类型加入 `video/quicktime`
- [x] 3.2 后端全量测试与前端构建通过，验证方式：`mvn test` 与 `npm run build` 退出码为 0