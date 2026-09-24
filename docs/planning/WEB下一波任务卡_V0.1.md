# WEB 下一波任务卡（N 波 · V1.1 延后项）V0.1 · 草稿

- 基线：`main@b06a51f`（`feat(web): add P4 catalog card grid (#305)`；第二波 X/B/F 与 V 波并行中）
- 依据：`docs/design/星枢NexusOne_WEB页面设计与前端开发规范_V1.1.md` **§25 P1 / §26 P2 / §27 P3 / §29 P5**；`docs/planning/WEB第二波任务卡_V0.1.md` 缺口表；`docs/planning/快进P4P5_范围冻结表_V0.1.md`；`docs/planning/WEB视觉还原任务卡_V0.1.md` / `快进任务人员安排清单_V0.1.md`（格式对齐，避免重叠）
- 范围：第二波明确**延后**的桌面 WEB 功能薄片。**不**做 H5、S2 治理、备份/恢复、开发者门户
- 执行方：本地 Codex / Cursor 按卡领取；NexusOne 只负责拆分与审阅

## 默认决策

1. **一卡一产品 + 一种资源类型或一个薄片**；后端 / 前端分卡；**新表 / Flyway 单独成卡**；**密钥 / Credential 安全敏感单独成卡**。
2. **先吃已有存货**：审计检索优先基于 `platform.audit_event` / `platform.audit_record`；发布中心优先聚合已有 form publish-log；流程设计器本波只做**只读查看**。
3. **租户隔离硬约束**：每个后端读/写必须带租户边界 + 跨租户否定测试；平台级接口须平台职责 + 审计。
4. **不编造数据**：空态「暂无」；演示数据只走 **V-SEED** 机制（本波不新建 V-SEED 卡）。
5. **大件薄切**：流程设计器编辑 / 发布定义标**后续波**；P5 仍**仅自用**，不做第三方开放平台。

## 所有任务通用约束

- 从最新 `origin/main` **新开分支 + 独立 worktree**；**禁止**在占用中的 `D:\NexusOne` 直接改；**禁止**动 `C:\Users\Administrator\.codex\worktrees\`。
- **一卡一 PR**；PR 只含本卡。
- UI 卡：只改 `web/src`，**排除** `web/src/mobile/**`；不改 Java（除非另开后端卡）。
- 后端卡：只改对应模块 / 必要 Flyway / 测试；**不改前端**。
- 迁移卡：只含 Flyway + 契约测试；不塞业务 API / UI。
- 安全卡：密钥只经 Secret/加密端口；响应永不回显明文；写审计。
- Flyway **冲突区单写**：迁移卡串行合入，禁止两人同改号段。
- 交付门禁：
  - UI：`npm test`、`npx vite build`、`npm run assert:h5`、`npm run assert:h5-every-page`、`git diff --check`
  - 后端：相关 Maven 模块测试绿；**含跨租户不可读/不可写测试**
- 原有深链保持可开。

---

## 一、分功能现状（已有 vs 缺失）

### P1 租户关系树（§25.4）

| | 内容 |
|---|---|
| **已有** | `platform.tenant`（V100，**无** parent/关系列）；`TenantController` `/api/v1/p1` 租户 CRUD/启停/config/entitlement/quotas；`GET .../tenants/{id}/audit-events`；FE `web/src/nav/p1.js` 项 `relations` 为 `later: true`；规范 Tree+Detail 模板 §17 |
| **缺失** | parent / 关系表与迁移；关系树 list/read API；FE client 与 `/p1/relations` 页；写/改挂（本波可后置） |

### P1 平台服务状态（§25.6）

| | 内容 |
|---|---|
| **已有** | `RuntimeMetaController` `GET /api/v1/meta/runtime`（schema_manager / crypto / secret 元信息）；导航 `service` = later |
| **缺失** | §25.6 所列 Database / Redis / MinIO·AIStor / Keycloak / Flowable / Quartz / Flyway 的**聚合只读**状态 API；FE 服务态页；**不要**做成专业 APM |

### P1 平台审计中心（§25.7）

| | 内容 |
|---|---|
| **已有** | `platform.audit_event`（租户侧 list）；`platform.audit_record` 哈希链（V501+）；`AuditTraceController` `GET /api/v1/audit/traces/{traceId}`；P3 页 `/p3/audit` 哈希链查看；导航 `audit` = later；TenantController 注释写明平台审计 API 将替换临时租户 list |
| **缺失** | 平台级**检索列表**（时间/租户/Actor/Action/Resource/Result/TraceId）；默认不返回敏感 Payload；FE 平台审计中心页 |

### P2 身份集成配置（§26.9，安全敏感）

| | 内容 |
|---|---|
| **已有** | `IdentityProviderPort` + `KeycloakIdentityProvider` / `StubIdentityProvider`；`IdentityConfiguration` 从 `IamProperties`（环境）注入 issuer/clientId/**clientSecret**；账号表 `idp_subject`；诚实文档声明未宣称 SAML/LDAP |
| **缺失** | 集成配置 CRUD 表/API；FE Provider Card（状态/最近同步/测试连接）；**密钥不得进普通 CRUD 卡** |

### P3 流程设计器（§27.6）

| | 内容 |
|---|---|
| **已有** | 类路径 BPMN `nexus-sequential-approval`；`ProcessController` `/api/v1/process` 仅实例启动/同意/驳回/撤回；`business_meta.process_*`（实例/待办/动作）；导航 `workflow-designer` = later |
| **缺失** | 流程**定义**读写 / 设计器 API；FE 设计器。本波只做**只读查看器**；节点编辑等标后续波 |

### P3 发布中心（§27.8）

| | 内容 |
|---|---|
| **已有** | `FormController` publish / publish-log / versions；`BusinessTypeController` provision；`business_meta.form_publish_log` / form_version；Form 工作室局部发布 UI；导航 `publish-center` = later |
| **缺失** | 跨对象统一发布中心聚合 list/read；FE 发布中心页；Expand/Migrate/Switch/Contract 提示（只读文案即可） |

### P5 开放 API 目录（§29.2–29.3，自用）

| | 内容 |
|---|---|
| **已有** | `ConnectController` `/api/v1/p5`：clients / webhooks / call-logs / connectors（B1 读模型已合）；导航 `apis` = later；X5 明确不做目录 |
| **缺失** | API 目录/注册表读模型与表；FE 开放接口目录表。第三方开发者门户整包后置 |

---

## 二、任务总览

| 编号 | 标题 | 产品 | 类型 | 依赖 | 可并行 | 预估 |
|---|---|---|---|---|---|---|
| N1 | P1 租户 parent 关系列迁移 | P1 | 迁移 | 无 | 与非同号段迁移串行；可与 N4/N6/N12/N14 并行 | 1h |
| N2 | P1 租户关系树只读 API | P1 | 后端 | N1 | 其后 N3 | 1–2h |
| N3 | P1 租户关系树页面 | P1 | 前端 | N2 | 与其它 FE（无同文件冲突） | 1–2h |
| N4 | P1 平台服务状态只读 API | P1 | 后端 | 无 | 是 | 1–2h |
| N5 | P1 平台服务状态页面 | P1 | 前端 | N4 | 是 | 1h |
| N6 | P1 平台审计检索 API | P1 | 后端 | 无 | 是 | 1–2h |
| N7 | P1 平台审计中心页面 | P1 | 前端 | N6 | 是 | 1–2h |
| N8 | P2 身份集成配置表迁移（无密钥列） | P2 | 迁移 | 无 | Flyway 串行 | 1h |
| N9 | P2 身份集成元数据 CRUD API | P2 | 后端 | N8 | 与 N10 协调接口 | 1–2h |
| N10 | P2 身份集成密钥写入/轮转 | P2 | **安全** | N8 | 建议 N9 后或紧挨 N9 | 1–2h |
| N11 | P2 身份集成配置页面 | P2 | 前端 | N9、N10 | 是 | 1–2h |
| N12 | P3 流程定义只读 API | P3 | 后端 | 无 | 是 | 1–2h |
| N13 | P3 流程设计器只读查看器 | P3 | 前端 | N12 | 是 | 1–2h |
| N14 | P3 发布中心聚合只读 API | P3 | 后端 | 无 | 是 | 1–2h |
| N15 | P3 发布中心页面 | P3 | 前端 | N14 | 是 | 1–2h |
| N16 | P5 开放 API 目录表迁移 | P5 | 迁移 | 无 | Flyway 串行 | 1h |
| N17 | P5 开放 API 目录只读 API | P5 | 后端 | N16 | 其后 N18 | 1–2h |
| N18 | P5 开放 API 目录页面 | P5 | 前端 | N17 | 是 | 1–2h |
| N19 | N4/N6 跨租户否定测试补强 | P1 | 后端（仅测试） | 无 | 是（与 N20 同模块，后合者 rebase） | 1h |
| N20 | N4 探活统一超时 | P1 | 后端 | 无 | 是（与 N19 同模块，后合者 rebase） | 1h |

建议分支前缀：`feat/n1-…` / `ui/n3-…` / `sec/n10-…` / `mig/n1-…`。

---

## 三、任务卡正文

### N1 — P1 租户 parent 关系列迁移

- **类型**：迁移　**分支**：`mig/n1-tenant-parent`　**前置**：无
- **目标**：为租户父子/集团关系提供最小持久化（仅 schema）。
- **区域**：`nexus-p1-tenant/.../db/migration/`（建议 `V102__p1_tenant_parent.sql` 或冲突区给定号段）；Flyway 契约测试。
- **建议字段**：`platform.tenant.parent_tenant_id uuid NULL REFERENCES platform.tenant(id)` + 索引；禁止自环的检查可放应用层（本卡可不加复杂约束）。
- **不做**：任何 REST / UI；不回填历史业务数据（空 parent 合法）。
- **验收**：全新库 migrate 成功；既有租户行可空 parent；模块测试绿。

### N2 — P1 租户关系树只读 API

- **状态**：已合并（PR #332），负责人 Cursor
- **类型**：后端　**分支**：`feat/n2-tenant-relation-tree-api`　**前置**：N1
- **目标**：只读关系树/子节点/祖先；租户隔离 + 跨租户否定测试。
- **区域**：`nexus-p1-tenant`（TenantController 或薄 RelationController）；仓储；MockMvc/集成测试。
- **建议端点（契约写进 PR）**：例如 `GET /api/v1/p1/tenants/tree`、`GET /api/v1/p1/tenants/{id}/children`、`GET /api/v1/p1/tenants/{id}/ancestors`（平台职责可读树；租户上下文不可越权看他户）。
- **不做**：改挂/拖拽写；跨租户业务数据下钻。
- **验收**：有 parent 时树正确；跨租户 403/空；测试绿；零前端文件。

### N3 — P1 租户关系树页面

- **类型**：前端　**分支**：`ui/n3-tenant-relation-tree`　**前置**：N2
- **目标**：§25.4 / §17 Tree+Detail；侧栏 `relations` 去 later。
- **区域**：`web/src/nav/p1.js`、`web/src/router.js`、`web/src/views/tenants/**`、`web/src/api/client.js`（仅 p1 读方法）。
- **验收**：左树右详情；空「暂无」；无假数据；UI 门禁全过；不改 Java。

### N4 — P1 平台服务状态只读 API

- **类型**：后端　**分支**：`feat/n4-platform-service-status`　**前置**：无
- **目标**：聚合 §25.6 组件探活（UP/DOWN/UNKNOWN + 简短说明），非 APM。
- **区域**：`nexus-bootstrap`（或 p1）新 Controller，例如 `GET /api/v1/p1/platform/services`；只读探测 DB/Redis/对象存储/Keycloak/Flowable/Quartz/Flyway（不可达 → UNKNOWN，禁止抛垮整个 API）。
- **不做**：新表；告警规则；历史时序。
- **验收**：平台职责可调；租户普通身份拒绝；单项失败不影响其它项；测试绿。

### N5 — P1 平台服务状态页面

- **类型**：前端　**分支**：`ui/n5-platform-service-status`　**前置**：N4
- **目标**：服务态列表/卡片；侧栏 `service` 去 later。
- **区域**：`web/src/nav/p1.js`、router、`views/tenants` 或 `views/platform`、api client。
- **验收**：真实探测结果或诚实 UNKNOWN；无假绿；UI 门禁；纯前端。

### N6 — P1 平台审计检索 API

- **类型**：后端　**分支**：`feat/n6-platform-audit-search`　**前置**：无（表已有）
- **目标**：平台级审计 list/search；过滤器对齐 §25.7；默认**不**返回敏感 Payload。
- **区域**：审计模块 + bootstrap/p1 Controller，例如 `GET /api/v1/audit/events`（query：from/to/tenantId/actor/action/resource/result/traceId/page）；基于 `platform.audit_record`（优先）或与 `audit_event` 的明确读模型；平台职责 + 租户范围测试。
- **不做**：改/删审计；默认完整 details；新大表（若仅需索引可另拆迁移卡，本卡默认不加）。
- **验收**：筛选可用；跨租户不可读他户；无密钥字段；测试绿。

### N7 — P1 平台审计中心页面

- **类型**：前端　**分支**：`ui/n7-platform-audit-center`　**前置**：N6
- **目标**：Filter + Table + 只读详情 Drawer；侧栏 `audit` 去 later；与 `/p3/audit` 哈希链页并存、职责不混。
- **区域**：nav/router/views/api。
- **验收**：URL 恢复筛选；空/错态；不展示 Secret；UI 门禁；纯前端。

### N8 — P2 身份集成配置表迁移（无密钥列）

- **类型**：迁移　**分支**：`mig/n8-idp-config`　**前置**：无
- **目标**：IdP 元数据表（无 client_secret 列）。
- **区域**：`nexus-p2-iam/.../db/migration/`（建议 `V201__…`）；字段示例：id、tenant_id（或平台级）、provider_type、display_name、issuer/status、last_sync_at、last_sync_ok/fail_count、created/updated。
- **不做**：密钥列；API；UI。
- **验收**：migrate 绿；契约测试证明无 secret 列。

### N9 — P2 身份集成元数据 CRUD API

- **类型**：后端　**分支**：`feat/n9-idp-metadata-api`　**前置**：N8
- **目标**：Provider 元数据 list/get/create/update；**响应不含密钥**；租户隔离。
- **区域**：`nexus-p2-iam` Controller/Service；测试。
- **不做**：密钥写入（归 N10）；SAML/LDAP 新产品宣称；真同步调度大件。
- **验收**：CRUD + 跨租户否定；GET 无 secret；测试绿。

### N10 — P2 身份集成密钥写入/轮转（安全）

- **类型**：安全　**分支**：`sec/n10-idp-secrets`　**前置**：N8（建议 N9 接口形状已定）
- **目标**：client secret 经 SecretPort/加密存储；Rotate；测试连接；**永不回显明文**；写审计。
- **区域**：`nexus-p2-iam` + crypto/secret 端口；危险操作确认由 FE 做，本卡保证 API 语义。
- **不做**：把 secret 写入普通配置表明文；日志打印 secret。
- **验收**：轮转后旧密钥失效；GET 元数据仍无明文；审计有记录；安全测试/用例在 PR 列出。

### N11 — P2 身份集成配置页面

- **类型**：前端　**分支**：`ui/n11-idp-integration`　**前置**：N9、N10
- **目标**：§26.9 Provider Card：状态/最近同步/失败数；动作：配置、测试连接、（可选）立即同步若 API 有；侧栏新增入口（当前 p2 nav 无该项）。
- **区域**：`web/src/nav/p2.js`、router、views、api。
- **验收**：密钥输入只写不回显；无假同步人数；UI 门禁；纯前端。

### N12 — P3 流程定义只读 API

- **状态**：已合并（PR #318），负责人 Cursor
- **类型**：后端　**分支**：`feat/n12-process-definition-read`　**前置**：无
- **目标**：对已部署/类路径流程键返回只读定义摘要（节点列表/边/BPMN 或 JSON 视图模型），供查看器。
- **区域**：`nexus-p3-studio` flow 包；例如 `GET /api/v1/process/definitions`、`GET /api/v1/process/definitions/{key}`。
- **不做**：保存草稿、改节点、发布新定义（**后续波**）；不新表（本波读类路径/引擎已部署定义）。
- **验收**：能读到 `nexusSequentialApproval`（或现键）；租户上下文下只读；测试绿。

### N13 — P3 流程设计器只读查看器

- **状态**：已合并（PR #324），负责人 Cursor
- **类型**：前端　**分支**：`ui/n13-process-designer-readonly`　**前置**：N12
- **目标**：三栏壳可先简化为「画布只读 + 节点属性只读」；侧栏 `workflow-designer` 去 later 或改为可点「查看」。
- **区域**：nav/router/`views/studio/**`、api。
- **验收**：可打开只读图；无编辑保存按钮（或 Disabled+说明后续）；UI 门禁。
- **后续波（本卡不做）**：节点编辑、Undo/Redo、Validate、Publish 定义。

### N14 — P3 发布中心聚合只读 API

- **状态**：已合并（PR #320），负责人 Cursor
- **类型**：后端　**分支**：`feat/n14-publish-center-read`　**前置**：无
- **目标**：聚合表单发布日志/版本状态（及可选 business-type provision 摘要）为发布中心 list/detail；状态枚举对齐 §27.8（Draft/Validating/Ready/Publishing/Running/Failed/RolledBack）能映射的做映射，不能的诚实返回原值。
- **区域**：`nexus-p3-studio`；例如 `GET /api/v1/p3/publish-center`、`GET .../{id}`。
- **不做**：新写路径；真 Schema Expand/Migrate 执行器。
- **验收**：有 publish-log 时列表非空；租户隔离；测试绿。

### N15 — P3 发布中心页面

- **状态**：已合并（PR #326），负责人 Cursor
- **类型**：前端　**分支**：`ui/n15-publish-center`　**前置**：N14
- **目标**：发布中心表 + 详情；只读提示「回滚不回滚共享物理 Schema…」；侧栏去 later。
- **区域**：nav/router/views/api。
- **验收**：数据来自 N14；无假成功率；UI 门禁。

### N16 — P5 开放 API 目录表迁移

- **类型**：迁移　**分支**：`mig/n16-open-api-catalog`　**前置**：无
- **目标**：自用 API 目录表（名称/method/path/version/scope/status 等）；**无**密钥列。
- **区域**：`nexus-p5-connect/.../V702__…`（号段以冲突区为准）。
- **不做**：开发者门户；对外 OAuth 客户端生态。
- **验收**：migrate 绿；契约无 secret 列。

### N17 — P5 开放 API 目录只读 API

- **类型**：后端　**分支**：`feat/n17-open-api-catalog-read`　**前置**：N16
- **目标**：list/detail 对齐 §29.2–29.3；租户隔离；可另提供最小管理写入**仅元数据**（若写入使卡 >2h则拆卡，本卡默认 list/read + 可选 seed 行由 V-SEED 后续）。
- **不做**：Credential；第三方门户；cURL 中夹带真密钥。
- **验收**：list/read + 跨租户否定；测试绿。

### N18 — P5 开放 API 目录页面

- **类型**：前端　**分支**：`ui/n18-open-api-catalog`　**前置**：N17
- **目标**：Table（Method Tag 色）；详情 Tabs 最小集 Overview/Request/Response/Scope；侧栏 `apis` 去 later。
- **区域**：`web/src/nav/p5.js`、router、`views/connect/**`、api。
- **验收**：无假调用量；空「暂无」；UI 门禁；文案保持**自用**不写「开放平台」。

### N19 — N4/N6 跨租户否定测试补强

- **状态**：已合并（PR #330），负责人 Cursor
- **类型**：后端（仅测试）　**分支**：`test/n19-n4-n6-tenant-negative`　**前置**：无
- **目标**：审阅 N4（#319/#322，`GET /api/v1/p1/platform/services`）与 N6（#321，`GET /api/v1/audit/events`）后的测试加固。N4：补 MockMvc/IT，普通租户身份调用 services 接口被拒（403）。N6：补 DB/IT，写入租户 A、B 审计事件，按 `tenantId=A` 查询断言无 B；非平台/租户身份调用被拒；响应不含 `details`。
- **不做**：改业务代码（除非测试暴露真实缺陷，则在 PR 说明并最小修复）；前端；迁移。
- **验收**：新测试绿，且在去掉权限检查时会失败（PR 写明验证方式）；约 1h。

### N20 — N4 探活统一超时

- **状态**：已合并（PR #331），负责人 Cursor
- **类型**：后端　**分支**：`feat/n20-n4-probe-timeout`　**前置**：无（与 N19 同模块，后合者 rebase）
- **目标**：为 N4 Redis / 对象存储 / Flyway 等探活加显式墙钟超时（建议每项 ≤2s，可配置）；超时 → `UNKNOWN`。可选并行探测，使总耗时 ≈ 最慢一项（当前串行且无 per-probe 超时）。
- **不做**：告警；历史时序；新表；前端。
- **验收**：模拟慢依赖的测试证明请求在上限内返回，且该项为 `UNKNOWN`、其余项正常；约 1h。

---

## 四、依赖关系图

```text
N1(mig) → N2(BE) → N3(FE)          # 租户关系树
N4(BE)  → N5(FE)                   # 平台服务态
N6(BE)  → N7(FE)                   # 平台审计中心
N8(mig) → N9(BE) ─┬→ N11(FE)       # 身份集成
          N10(SEC)┘
N12(BE) → N13(FE)                  # 流程只读设计器
N14(BE) → N15(FE)                  # 发布中心
N16(mig)→ N17(BE) → N18(FE)        # 开放 API 目录
N19(test)                          # N4/N6 跨租户否定测试（独立）
N20(BE)                            # N4 探活统一超时（独立；与 N19 同模块后合 rebase）

Flyway 冲突区：N1 / N8 / N16 串行合入（不同号段可开发并行，合入排队）
```

---

## 五、建议第一批下发

| 批次 | 卡 | 理由 |
|---|---|---|
| **Batch 0（可立即并行）** | **N4、N6、N12、N14** | 无新表依赖；四条后端只读聚合/探活，互不挡 |
| **Batch 0b（迁移单写排队）** | **N1 → 再 N8 → 再 N16** | Flyway 单写；开发可预读，合入严格串行 |
| **Batch 1** | N2（等 N1）、N9+N10（等 N8）、N17（等 N16）、以及 Batch0 对应 FE：N5/N7/N13/N15 | 解锁页面 |
| **Batch 2** | N3、N11、N18 | FE 收尾 |

优先业务价值：**N6/N7（审计）**、**N4/N5（服务态）**、**N12/N13（流程只读）** 可最快见页；关系树与 IdP 安全链略长。

---

## 六、本波不做 / 后续波登记

| 项 | 处理 |
|---|---|
| H5（`/m/*`、`web/src/mobile/**`） | 整波延后 |
| S2 治理 | 冻结表后置 |
| 备份/恢复 / RecoveryPort 真恢复 | 冻结表整包后置 |
| 开发者门户 / 外部 OAuth / 第三方开放平台 | 冻结表整包后置 |
| P5 Credential 大件 | 仍后置（X5/B1 未交付则进 B2/N 后续） |
| 流程设计器：节点编辑 / 草稿保存 / Validate / Publish 定义 | **后续波**（N13 之后） |
| 租户关系：改挂写 API / 拖拽 | **后续波**（N3 之后） |
| IdP：SAML/LDAP 产品化、同步调度大件 | **后续波**（诚实文档未宣称前不做） |
| 发布中心：真 Schema Expand/Migrate 执行 | **后续波** |
| 商业权益运营分析等 P4 later | 不在本波（仍归 P4 后续） |

---

## 七、修订记录

- 2026-09-24：NexusOne 草稿 V0.1。对照 `main@b06a51f` 只读 worktree 与第二波缺口表。
- 2026-09-24：N12 已合并（PR #318）。P3 流程定义只读 API，读类路径 BPMN，不发布新定义。
- 2026-09-24：N14 已合并（PR #320）。P3 发布中心只读聚合，不执行 Schema 变更。
- 2026-09-24：N13 已合并（PR #324）。P3 流程设计器只读查看，不编辑、不发布定义。
- 2026-09-24：N15 已合并（PR #326）。P3 发布中心页面只读展示，不执行 Schema 变更。
- 2026-09-24：NexusOne 追加 N19、N20（N4/N6 审阅后的加固卡）。
- 2026-09-24：N19 已合并（PR #330）。租户身份不能读平台服务态和审计检索；审计查询不串租户、不含 details。
- 2026-09-24：N20 已合并（PR #331）。平台服务态每项探活有墙钟超时，超时记为 UNKNOWN。
- 2026-09-24：N2 已合并（PR #332）。租户关系树只读，租户上下文不能看其他租户。