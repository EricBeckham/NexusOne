# 演示机构建辅助（M6）

对照：[demo-host-prod-deployment.md](../../docs/engineering/runbooks/demo-host-prod-deployment.md)（P6）。

| 文件 | 用途 |
|---|---|
| [demo-host-prod.env.template](../../docs/engineering/runbooks/demo-host-prod.env.template) | 可填 env 占位符；**禁止**写入真实密钥后提交 |
| `build-bootstrap-jar-sha.ps1` / `.sh` | 本机构建 `nexus-bootstrap` JAR 并打印 SHA-256 |

**模板 ≠ 已部署。** 本目录脚本不连接外网主机、不注入密钥、不传输 JAR。
