# P0-06 MinIO / AIStor S3 契约验证资产

## 运行

此独立 SDK PoC 需要 Java 8+、Maven、访问 MinIO/AIStor 的 S3 endpoint，以及**预先创建的专用测试 bucket**；产品基线仍为 Java 21，需另做目标运行环境验收。在 PowerShell 中设置 `S3_ENDPOINT`、`S3_ACCESS_KEY`、`S3_SECRET_KEY`、`S3_TEST_BUCKET`，运行 `./run.ps1`。脚本会编译并先执行本地租户路径/签名守卫断言，再执行真实 S3 上传、下载、11 MiB 分片上传及预签名 URL 断言，最后删除本轮生成的对象。缺少任何 S3 变量时，真实 S3 测试明确输出 `SKIP`。

可用 `compose.yaml` 起一个专用测试实例；需自行提供审核过的 `MINIO_IMAGE` **不可变 digest** 和测试凭据，然后 `docker compose up -d`。当前环境无 Docker/MinIO 服务，因此未在本机启动该实例。AIStor 企业镜像、授权及镜像来源须由采购/交付团队提供，不能用社区镜像代替验证。

## 验证范围

- 本地：`/{env}/{tenant_id}/{business_type}/{yyyy}/{mm}/{uuid}` 前缀；拒绝路径穿越；签名 URL 前重验租户、Subject、资源权限、文件所有权及对象前缀；拒绝时未调用 S3 签名 API。
- 真实 S3（仅配置后运行）：单次上传/下载；11 MiB 对象以 5 MiB part size 上传并校验字节；预签名 GET URL 实际下载并校验内容。测试使用符合租户前缀约定的临时 UUID 对象键并清理。
- **未验证**：目标产品具体镜像 digest/版本、授权/支持来源、升级、备份恢复、ARM64；这些仍为 P0-06 门禁。当前测试也未覆盖 Copy/Metadata、文件大小/MIME/Checksum/生命周期策略。Delete 仅用于测试清理，未单独断言。

SDK 使用 `io.minio:minio:9.0.3`，版本和 API 参考 [MinIO Java SDK 官方 README](https://github.com/minio/minio-java/blob/master/README.md) 与 [API 文档](https://github.com/minio/minio-java/blob/master/docs/API.md)。
