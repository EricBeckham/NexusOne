# DL-L5 Keycloak / prod 环境守卫契约（V0.1）

- 卡片：[`WEB上线演示任务卡_DL波_V0.1.md`](WEB上线演示任务卡_DL波_V0.1.md) · DL-L5
- 行为来源：DPO-L1（#659）`ProductionProfileGuard.keycloakViolations`；DL-L3（#685）把判定下沉为 `keycloakPresence`，就绪 API 与拒启共用同一套规则
- 真机验收：见 [`M7-Keycloak演示验收清单.md`](M7-Keycloak演示验收清单.md) §1。**本契约只是预备，≠ 验收**

## 1. 契约

`prod` profile 且 `nexus.iam.idp=keycloak` 时，四个变量缺一不可：

| 变量（template 名） | 属性 | 视为缺失 |
|---|---|---|
| `NEXUS_IAM_KEYCLOAK_ISSUER` | `nexus.iam.keycloak.issuer` | 空白；含 `REPLACE_`；等于本地默认 `http://127.0.0.1:8080/realms/nexus` |
| `NEXUS_IAM_KEYCLOAK_CLIENT_ID` | `nexus.iam.keycloak.client-id` | 空白；含 `REPLACE_` |
| `NEXUS_IAM_KEYCLOAK_CLIENT_SECRET` | `nexus.iam.keycloak.client-secret` | 空白；含 `REPLACE_` |
| `NEXUS_IAM_KEYCLOAK_REDIRECT_URI` | `nexus.iam.keycloak.redirect-uri` | 空白；含 `REPLACE_`；等于本地默认回调 |

- 失败：启动被拒（`prod profile refused to start`），每个缺项一行，**只写变量名和属性名**，按上表顺序。
- **永不**出现在任何消息、异常、日志中的：secret、client id、issuer / 回调主机、主密钥、`REPLACE_*` 占位原文、本地默认地址。
- 警告（issuer 不是 https、bootstrap token 仍设置等）同样只写属性名。
- 旧变量名 `NEXUS_KEYCLOAK_*`（`application.yml` 占位）仍可让属性生效；消息统一使用 template 中的 `NEXUS_IAM_KEYCLOAK_*`。

## 2. 测试（`nexus-bootstrap`）

| 测试 | 覆盖 |
|---|---|
| `ProductionProfileGuardTest`（DPO-L1） | 缺失形态、relaxed binding、非 prod / 非 keycloak 不检查 |
| `KeycloakProdEnvGuardContractTest`（DL-L5） | 变量 × 缺失形态矩阵（10 例）：每例恰好一条、只点名该变量、不回显值；四项全缺按顺序点名；警告与成功路径的**日志**（logback 捕获）不含值 |
| `KeycloakProdStartupRefusalTest`（DL-L5） | 真实 `NexusOneApplication` 以 `prod` 启动、故意缺 `REDIRECT_URI`：启动失败；全部控制台输出与异常链包含变量名，不含 secret / client id / issuer 主机 / 主密钥 |
| `DemoReadinessWireTest`（DL-L3） | 就绪 API 同源 presence，序列化后无值 |

命令：`mvn -B -pl nexus-bootstrap -am test -Dtest=KeycloakProd*Test,ProductionProfileGuardTest`
