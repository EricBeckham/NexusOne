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

该脚本拒绝未启用演示开关、生产环境标记或非回环数据库地址；需要本机 `psql`。它只删除 P1 卡固定的演示租户 ID 及其配额幂等记录，不触碰保留的 `platform` 租户或其他租户。

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
