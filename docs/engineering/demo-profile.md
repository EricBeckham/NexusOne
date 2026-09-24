# 演示开关

**禁止连生产 / 默认关闭**

这不是正式镜像的默认入口。不设置开关时，进程不会进入演示档，也不会因为本卡插入任何业务行。

## 开关

| 项 | 默认 | 打开后 |
|---|---|---|
| `NEXUS_DEMO_ENABLED` | 不设，等价 `false` | `true` |
| Spring profile `demo` | 不激活 | `application-demo.yaml` 把 `nexus.demo.enabled` 设为 `true` |
| `NEXUS_DEMO_ENVIRONMENT` | `local` | 若是 `prod` 或 `production`，启动直接失败 |

两条路效果相同，都要经过同一道门：数据库地址必须是本机（`127.0.0.1`、`localhost` 或 `[::1]`）。远程地址一律视为生产形态，拒绝启动。

本卡不创建表，不写种子行。`nexus.studio.seed-demo-metadata` 是既有的内存元数据替身，不属于这个演示档，本卡不改它的默认值。

## 启用

只在本机、当前终端生效。不要写进系统环境，不要写进正式启动脚本。

PowerShell（当前窗口）：

```powershell
. .\scripts\demo\enable.ps1
```

bash（当前 shell）：

```bash
source scripts/demo/enable.sh
```

等价的手工写法：

```text
NEXUS_DEMO_ENABLED=true
NEXUS_DEMO_ENVIRONMENT=local
```

或在启动参数里增加 `--spring.profiles.active=demo`。不要把 `demo` 放进默认 profile。

打开之后重启进程。日志会出现「演示开关已打开。禁止连生产。」。V-SEED-P1…P5 的种子初始化器会再次检查开关和本机数据库地址；开关关闭时不写任何行。

### 清理 P1 演示租户

P1 种子只在既有 `platform` 表插入固定 ID 的演示租户、配置、权益投影和配额，不新增表或迁移。清理前保持 `NEXUS_DEMO_ENABLED=true`（或 `demo` profile），并使用和应用相同的本机 `NEXUS_DB_URL`：

```powershell
.\scripts\demo\clean-p1.ps1
```

先停止应用。该脚本拒绝未启用演示开关、生产环境标记或非回环数据库地址；需要本机 `psql`。它只删除 P1 卡固定的演示租户 ID 及其配额幂等记录，不触碰保留的 `platform` 租户或其他租户。

### 清理 P2 演示组织与角色

P2 种子只在 P1 固定演示租户 ID 下创建两个无登录凭据的示例人员、部门、岗位、任职和一个无授权格的角色/分配。示例邮箱使用保留的 `.invalid` 域名；不创建可认证的 Keycloak 用户、会话或任何权限授权。清理同样要求演示开关与本机 PostgreSQL：

```powershell
.\scripts\demo\clean-p2.ps1
```

脚本只删除 V-SEED-P2 使用的固定账号、人员、组织、任职和角色 ID，不清理 P1 租户，也不删除其他业务行。

清理时先停止应用，保持演示开关打开并按产品顺序清理（先 P2，再 P1），完成后运行 `scripts/demo/cleanup.ps1` 关闭开关，再重启应用；否则下次启动会按设计重新播种。

### 清理 P3 演示应用与台账草稿

P3 种子在固定演示租户下创建一个应用、一个 DRAFT 台账元数据和两个字段定义。台账故意保持 DRAFT，不调用动态建表服务，不生成 `business_data` 物理表，也不创建表迁移。使用 guarded 脚本清理固定 ID：

```powershell
.\scripts\demo\clean-p3.ps1
```

先停止应用；清理时需要演示开关仍为真和本机数据库地址。此脚本只删除 P3 固定台账、字段和应用元数据，并按本卡幂等键撤销两项配额占用，不删除 P1 基线配额。

### 清理 P4 演示市场目录

P4 种子在市场目录创建一个已发布应用、一个已发布版本和一个只描述演示权益的 SKU；不会为租户创建订阅或安装。使用 guarded 脚本按固定 ID 清理：

```powershell
.\scripts\demo\clean-p4.ps1
```

先停止应用；清理时需要演示开关仍为真和本机数据库地址。清理范围仅限 P4 固定应用包、版本及 SKU。

### 清理 P5 演示连接摘要

P5 种子创建一个已停用客户端（固定全零 digest、`active=false`）、一条历史 API 调用摘要和一条成功的自检连接器运行摘要；不创建 Webhook 订阅，也不生成或保存客户端明文密钥、Webhook 签名密钥。使用 guarded 脚本清理：

```powershell
.\scripts\demo\clean-p5.ps1
```

先停止应用；清理时需要演示开关仍为真和本机数据库地址。清理范围仅限 P5 固定调用日志、连接器运行和停用客户端记录。

## 清理

V-SEED-0 本身没有插入业务行。关闭开关会阻止后续启动写入新的演示数据；已有种子须先运行各自产品卡提供的清理脚本。

PowerShell：

```powershell
. .\scripts\demo\cleanup.ps1
```

bash：

```bash
source scripts/demo/cleanup.sh
```

手工清理：去掉 `NEXUS_DEMO_ENABLED`，并从 `SPRING_PROFILES_ACTIVE` 里拿掉 `demo`，然后重启。不需要删表。后续产品种子卡如果写入了行，必须在各自的卡里提供清理，不能把清理变成默认启动步骤。
