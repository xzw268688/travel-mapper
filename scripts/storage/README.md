# 私有 S3 兼容存储（SeaweedFS）

MinIO 镜像与 Windows 二进制当前不可获取时，可用 SeaweedFS 验证私有桶行为。`seaweedfs-s3.json` 定义访问身份；配置后匿名请求返回 403。

## 启动

```bash
# Git Bash 需要关闭路径转换，避免 /etc/... 被改写为 Windows 路径
MSYS_NO_PATHCONV=1 docker run -d --name tm-seaweed \
  -p 19091:8333 -p 19333:9333 -p 18888:8888 \
  -v "D:/FileStorage/Desktop/travel-mapper/scripts/storage/seaweedfs-s3.json:/etc/seaweedfs/s3.json:ro" \
  chrislusf/seaweedfs:latest \
  server -s3 -s3.port=8333 -s3.config=/etc/seaweedfs/s3.json \
  -master.port=9333 -filer.port=8888
```

S3 端点：`http://127.0.0.1:19091`，accessKey/secretKey 均为 `test`。

## 创建桶

```bash
printf 's3.bucket.create -name travelmapper\ns3.bucket.create -name travelmapper2\ns3.bucket.list\n' \
  | docker exec -i tm-seaweed weed shell -master=127.0.0.1:9333
```

## 验证私有桶

```bash
# 匿名访问应返回 403
curl -o /dev/null -w '%{http_code}\n' http://127.0.0.1:19091/travelmapper/nonexistent.jpg
```

运行存储集成测试（包含 `privateBucketRejectsUnsignedAccess`）：

```bash
cd backend
mvn -Dtest=StorageIntegrationTest test \
  -Dtest.containers.enabled=false \
  -Dtest.mysql.url='jdbc:mysql://127.0.0.1:13306/travelmapper?...' \
  -Dtest.mysql.username=travelmapper -Dtest.mysql.password=travelmapper \
  -Dtest.mysql.admin-username=root -Dtest.mysql.admin-password=root \
  -Dtest.redis.host=127.0.0.1 -Dtest.redis.port=16379 \
  -Dtest.object-storage.endpoint=http://127.0.0.1:19091 \
  -Dtest.object-storage.access-key=test -Dtest.object-storage.secret-key=test \
  -Dtest.object-storage.private-enforced=true
```
