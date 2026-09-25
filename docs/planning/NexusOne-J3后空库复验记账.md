# NexusOne J3 后空库复验记账

日期：2026-09-25（Asia/Shanghai）。基线：`origin/main@9c24923b`（含 J3 #450、K20 种子顺序跟进 #451、流程草稿 401 提示 #452）。分支：`nexusone/j3-post-followup-resmoke`。本卡只记录本机 HTTP / 库事实，**不改产品代码**；未使用外网演示机或真实 Keycloak。

## 环境

- Worktree：`D:\Projects\NexusOne-nexusone`。JDK 21、Maven 3.9.11 执行 `mvn -B -q -DskipTests package`，退出码 0。
- PostgreSQL 15.19 全新一次性集群建在 worktree 的 `.local/j3-resmoke-pg`，只监听 `127.0.0.1:55445`，使用空 `postgres` 库；未连接已有 `nexusone` 或 `5432`。启动后 Flyway 迁移到 **705**。
- 后端 `demo` profile、stub IdP、IAM / permission JDBC、`NEXUS_CACHE_MODE=memory`；仅为本机平台接口打开 `NEXUS_P1_DEMO_CONTEXT_ENABLED=true`。启动日志出现演示开关已打开、禁止连生产。
- Windows NIO：`-Djdk.net.unixdomain.tmpdir=<worktree>\.local\tmp`（与 J3 备注一致）。
- 驱动方式：浏览器自动化不可用时，按 J3/I8 与 `PublishedTemplateVersionsTest` / `DemoSeedPostgresBootTest` 同源 HTTP 路径复验。平台头 actor `01900000-0000-7000-8000-0000000000ac`；租户路径 stub token 登录 `lin.xiao@demo.invalid` / `zhou.ran@demo.invalid`（subject `demo-seed:p2:lin|zhou`）。
- 未启动 Vite；未声称外网 demo host / 真实 Keycloak。

## 平台路径（create → validate → publish → bind）

| 步骤 | 结果 | 证据 |
|---|---|---|
| 首启空库种子流程草稿（#451） | **通过** | `business_meta.process_definition_draft` 中 demo 租户 `…0101` + `demoSimpleApproval` **count=1**。林晓会话 `GET /api/v1/process/drafts` 返回该草稿；`GET …/demoSimpleApproval/publication` → version **1**。 |
| 列出种子表单模板 | 通过 | `GET /api/v1/p1/platform/form-templates` 含 `demo_simple_approval` / `PUBLISHED`。 |
| 新建模板草稿 | **通过** | `POST …/form-templates` code=`j3_post_resmoke`，id=`4884d924-2c92-43c9-82cc-98f125c33ad6`，status=`DRAFT`。字段 `text_1`（TEXT）+ `person_1`（PERSON）。 |
| 校验 | **通过** | `POST …/validate` → `valid=true`，status=`VALIDATED`。 |
| 发布 | **通过** | `POST …/publish` → version=1，`version_id=d0226e0f-f2a0-47f0-9bad-23123a51e977`；列表 status=`PUBLISHED`，`running_version_id` 非空。 |
| 无租户会话读流程草稿（#452 前置） | 通过（API 事实） | `GET /api/v1/process/drafts`（仅平台头）→ **401** `P2.IAM.UNAUTHORIZED` `"missing bearer token"`（与 J3 观察 A / #452 一致）。本卡未跑浏览器，故不复验前端文案；#452 已合入 main。 |
| 用**种子流程**绑新产品（#451 是否可直接选用） | **通过** | `POST /api/v1/p4/product-definitions` code=`j3_post_resmoke_product`，`form_template_version_id=d0226e0f-…`，`process_definition_key=demoSimpleApproval`，version=1 → **201**，id=`2dea91cd-e813-4d5d-ab00-4c09374142c6`。**无需**再走 UI 新发流程。 |
| 绑定 SKU | **通过** | `PUT …/product-definitions/{id}/skus/demo.project-hub.standard` → 200（选用未绑定 SKU，避免改绑演示简单审批）。 |
| 目录 / 审计复核 | 通过 | `GET /api/v1/p4/catalog` 含 `j3_post_resmoke_product`。`platform.audit_event` 中 `platform.%` 且 `tenant_id IS NULL` = **0**；created/validated/published/product_definition.created/sku_bound 共 **5** 条。 |

**平台路径结论**：通过。#451 后首启空库种子流程可选；绑定未再依赖 UI 补发流程。

## 租户路径（I8：订阅 → 发起 → 审批含意见 → 我的申请）

| 步骤 | 结果 | 证据 |
|---|---|---|
| 林晓 stub 登录 | 通过 | `POST /api/v1/auth/login` → tenant=`…0101`。 |
| 订阅开通 | **通过** | `POST /api/v1/p4/tenants/…0101/subscriptions` sku=`demo.simple-approval` version=`…0504` → **201**，installation=`08b49860-cc9e-404c-947e-46e4f24cac3a`。 |
| 发起申请 | **通过** | 表单字段 `title`+`approver`；`POST …/product-applications/installations/{id}` values title=`J3后空库复验-租户路径`、approver=周然 person `…0204` → **201**，appId=`01a0d8e4-f361-7b8a-bd82-91be6e4df3bc`，status=`RUNNING`。 |
| 周然待办 | 通过 | `GET /api/v1/p3/todos` 见 todo=`01a0d8e4-f3fa-7b01-8499-75e485314045`，business_key=`product-application:01a0d8e4-…`。 |
| 周然审批（含意见） | **通过** | `POST /api/v1/process/tasks/{todoId}/approve` body comment=`J3后空库复验审批通过` → **200**，outcome=`APPROVE`，instance_status=`APPROVED`，comment 回写。 |
| 林晓我的申请读回 | **通过** | `GET /api/v1/p3/product-applications`：该单 status=`APPROVED`，comment 与审批意见一致；`GET /api/v1/todos/initiated` status=`APPROVED`；周然 `GET /api/v1/todos/done` 含该 todo。 |

**租户路径结论**：通过（HTTP，同源 stub 身份，非真实 Keycloak）。

## 新断点

- **无新产品断点。** J3 断点 B（首启种子流程草稿缺失）在 #451 后本机空库**未复现**。J3 观察 A（无会话读 drafts 401）API 行为仍在；前端提示由 #452 覆盖，本卡未浏览器复验文案。
- 操作备注（非断点）：产品申请审批须走 `/api/v1/process/tasks/{todoId}/approve`（`business_key` 以 `product-application:` 开头时），`/api/v1/p3/path/tasks/.../approve` 返回 400，属既有客户端分流，非新回归。

## 收尾

后端进程与一次性 PostgreSQL（`55445`）将在记账提交后停止并删除 `.local/j3-resmoke-pg`、`.local/tmp` 工作文件；端口 `8080`/`55445` 释放。主克隆 `D:\Projects\NexusOne` 未改动。
