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

打开之后重启进程。日志会出现「演示开关已打开。禁止连生产。本进程不插入业务行。」此时仍然没有演示业务行；那些行属于后续 V-SEED-P1…P5，而且只有开关为真时才允许写入。

## 清理

本卡没有插入业务行。清理就是关掉开关并重启。

PowerShell：

```powershell
. .\scripts\demo\cleanup.ps1
```

bash：

```bash
source scripts/demo/cleanup.sh
```

手工清理：去掉 `NEXUS_DEMO_ENABLED`，并从 `SPRING_PROFILES_ACTIVE` 里拿掉 `demo`，然后重启。不需要删表。后续产品种子卡如果写入了行，必须在各自的卡里提供清理，不能把清理变成默认启动步骤。
