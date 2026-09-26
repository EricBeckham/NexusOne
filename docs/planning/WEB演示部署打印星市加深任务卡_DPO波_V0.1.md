# WEB 演示部署·打印加深·星市运营任务卡（DPO 波）V0.1

- **基线**：`origin/main` @ `3d103de2`（CF-RD5 账本已合，#655）；**Flyway 当前最高版本 = V706**（`nexus-p1-tenant/.../V706__tenant_self_reg_and_activation_application.sql`）；`outOfOrder` 保持关闭
- **依据**：
  - Axis A：[`M7-Keycloak演示验收清单.md`](M7-Keycloak演示验收清单.md)、[`demo-host-prod-deployment.md`](../engineering/runbooks/demo-host-prod-deployment.md)、[`demo-host-prod.env.template`](../engineering/runbooks/demo-host-prod.env.template)
  - Axis B：[`CF-RD5_打印设计器能力边界记账_V0.1.md`](CF-RD5_打印设计器能力边界记账_V0.1.md)（CF-RD1～RD4 已合；later + 已知缺口）
  - Axis C：[`XB15-XB波打勾记账.md`](XB15-XB波打勾记账.md)（**XB1–XB15 已全部打勾**）；规划 PR [#566](https://github.com/EricBeckham/NexusOne/pull/566) **仍 open**
- **执行方**：**Cursor / Claude / Codex** 各 ≥5 张薄卡；**NexusOne 只审查（review）**，本波**不派** NexusOne 实现卡
- **工作树 / 分支前缀（实现时）**：Cursor → `cursor/`；Claude → `claude/`；Codex → `codex/`；本规划 docs PR → `nexusone/`
- **性质**：**docs-only**。本文件不改产品代码。

## 1. 目标（三轴）

| 轴 | 代号 | 目标 | 不做 |
|---|---|---|---|
| **A 演示真部署** | Demo / Keycloak 预备 | 为演示机真主机 + Keycloak 做**可合入的预备**：compose stub、runbook/脚本、配置校验（不打印 secret）、FE 登录壳、可跳过 smoke、契约测、更新 M7 | **不**声称 prod Keycloak 已验收；真实主机 / DNS / 密钥仓 / Keycloak 管理台 = **外部阻塞**（见 M7 §2） |
| **B 打印/配置加深** | beyond CF-RD MVP | 在 CF-RD 边界之上：持久化+历史（**下一全局 Flyway = V707**）、多模板元数据、薄拖拽切片（若有）、分页/页眉 later 薄、真数据预览或 PDF 二选一薄片、设计器纸张+修订守卫、修 CF-R1 PUT 缺 `expected_revision`、合并重复 PUT client、SpringBootTest | **不**整页拖拽排版引擎；**不**夹带 UI 的 Flyway；迁移专卡 |
| **C 星市/运营加深** | Ops deepen | 在已有 list / upgrade API 上加深运营页；升级步骤记录向真实值升级（无编排前保持 **UNKNOWN**）；schema 专卡；收口 #566 | **不**重做 XB1–XB5 壳；**禁止 H5**；**禁止伪造支付**；**禁止假绿升级**（DONE 仅当真实运维存在） |

## 2. 边界与硬禁

### 2.1 硬禁（全波）

- **禁止 H5** / `web/src/mobile/**`
- **禁止假绿升级**：stepper 不得在无真实编排/记录时标 DONE
- **禁止发明支付** / 计费 / 价格字段
- **Flyway**：仅下一全局版本（当前最高 **V706** → 下一张迁移卡用 **V707**）；`outOfOrder` **关闭**；迁移与产品 UI/业务逻辑分卡
- **禁止**在 FE / 日志 / PR / 脚本输出中打印真实 secret
- **禁止**声称「演示机 prod Keycloak 已验收」——本波卡片仅预备
- **禁止**重做 XB1–XB5 运营壳 / 导航 / 空态已合范围
- NexusOne：**只审查**，不领实现卡

### 2.2 外部阻塞（Axis A，不可由 agent 消除）

| 阻塞 | 说明 |
|---|---|
| 真机主机 / SSH / 云账号 | 需 Eric / 运维 |
| DNS / 公网 HTTPS 证书 | 需域名与证书私钥 |
| 密钥仓中的真实 `CLIENT_SECRET` / `MASTER_KEY` / DB 密码 | 禁止写入仓库与 agent 会话 |
| 演示机 Keycloak realm 管理台 | 需 IdP 管理权 |
| 受控演示账号真实凭据 | 只需记录显示名 / subject，不写口令 |

### 2.3 Flyway 记账

| 项 | 值 |
|---|---|
| 扫库日 | 2026-09-27（Asia/Shanghai） |
| **当前最高版本** | **V706** |
| 文件 | `nexus-p1-tenant/src/main/resources/db/migration/V706__tenant_self_reg_and_activation_application.sql` |
| 本波下一迁移 | **V707**（打印持久化 **或** 升级步骤 schema，**分卡、分 PR**；先合入者占 V707，后者顺延） |
| outOfOrder | **off** |

## 3. 与既有波次关系

| 既有 | 本波态度 |
|---|---|
| M6/M7 演示机 runbook + Keycloak 清单 | **加深预备**；更新交叉链接；不宣称验收通过 |
| CF-RD1～RD4 / CF-R1～R4 | **在其上加深**；修已知缺口（R1 PUT 缺修订、重复 client 名） |
| XB1～XB15（#566 规划仍 open） | **全部已合实现**；本波只 deepen + 建议合入或关闭 #566；不重做壳 |

**#566 建议**：本 DPO 规划 docs 落地后，将 #566 **合并**（若仍为纯规划且与 XB15 账本一致）或 **关闭并注明已被 XB15 + 本 DPO 账本 supersede**——见 **DPO-X6**。

---

## 4. 薄卡总表

合计：**18** 张（Cursor 6 + Claude 6 + Codex 6）。每张 = 一个薄切片 / 一个 PR / 约 1–2h；**FE ≠ BE**；迁移与 secrets 独立成卡。

### 4.1 Cursor（FE-heavy）— DPO-C1…C6

---

#### DPO-C1

| 字段 | 内容 |
|---|---|
| **id** | DPO-C1 |
| **title** | Axis A：Web 登录/回调壳 — 当 Keycloak issuer 已配置时走 IdP 路径（FE 不持有任何 secret） |
| **owner** | Cursor |
| **axis** | A 演示真部署 |
| **branch（实现时）** | `cursor/dpo-c1-keycloak-login-shell` |
| **in scope** | 检测公开配置 / 构建时 `VITE_NEXUS_KEYCLOAK_ISSUER`（或等价公开 issuer 标志）存在时，登录入口跳转/展示 Keycloak 回调壳；issuer 未配置时保持现有 stub 路径并诚实文案；**零 secret 进入 FE bundle** |
| **out of scope** | 真机验收；写入 client secret；改 BE 校验（→ DPO-L1）；compose（→ DPO-X1） |
| **acceptance** | ① issuer 配置存在 → UI 露出 Keycloak 登录入口且无 secret 字符串；② 未配置 → stub 路径仍可用 + 文案说明「演示 IdP 未配置」；③ vitest 覆盖两种模式；④ `npm run build` 产物 grep 无 `CLIENT_SECRET` / 占位 REPLACE_ 以外的疑似密钥 |
| **likely paths** | `web/src/views/**/Login*`、`web/src/api/**`、`web/.env*`（仅公开变量文档）、相关 `*.test.js` |
| **deps** | 无（可与 L1/X1 并行；联调需 L1+X1） |
| **test plan** | vitest：issuer on/off 两分支；手动 stub 登录仍通 |

---

#### DPO-C2

| 字段 | 内容 |
|---|---|
| **id** | DPO-C2 |
| **title** | Axis A：演示部署状态页 / 清单 UI（只读）— 公共 health + 配置「是否存在」标志，不展示 secret 值 |
| **owner** | Cursor |
| **axis** | A |
| **branch** | `cursor/dpo-c2-demo-deploy-status` |
| **in scope** | 只读页：聚合 `/actuator/health`（或已有公开健康口）+「Keycloak issuer 已配置 / 未配置」「prod profile 标志」等 **presence boolean**；对照 M7 清单条目显示 待外部 / 可本地；**永不渲染 secret 值** |
| **out of scope** | 写配置；调用管理 API；真实 Keycloak admin；后端 fail-fast（L1） |
| **acceptance** | ① 页面只读；② 无任何密码/secret 字段展示；③ health 失败时诚实空错态（S 壳）；④ 单测 mock health |
| **likely paths** | `web/src/views/**/DemoDeploy*` 或运营/平台只读页扩展、router、vitest |
| **deps** | 建议在 C1 后；可与 X2 文案对齐 |
| **test plan** | vitest mock health 200/503；断言 DOM 无 secret 模式 |

---

#### DPO-C3

| 字段 | 内容 |
|---|---|
| **id** | DPO-C3 |
| **title** | Axis B：CF-R1 样例页 PUT 补发 `expected_revision` + 409 冲突 UI |
| **owner** | Cursor |
| **axis** | B 打印加深 |
| **branch** | `cursor/dpo-c3-sample-put-revision` |
| **in scope** | `ProductSampleView` 保存映射时带上当前 `expected_revision`（与设计器 RD3 行为对齐）；409 时展示冲突壳（刷新服务器版本 / 确认覆盖策略与设计器一致，**禁止默默覆盖**） |
| **out of scope** | BE 持久化（L2/L3）；合并 client 名（C4）；设计器纸张控件（C5） |
| **acceptance** | ① PUT body 含 `expected_revision`；② 模拟 409 → 冲突 UI，数据不丢草稿；③ vitest 覆盖成功路径与 409；④ 与 RD3 文案语气一致 |
| **likely paths** | `web/src/views/market/ProductSampleView.vue`、`ProductSampleView.test.js`、`web/src/api/client.js`（若需） |
| **deps** | 无（修已知缺口；见 CF-RD5 §4.1） |
| **test plan** | vitest：assert PUT payload；409 handler |

---

#### DPO-C4

| 字段 | 内容 |
|---|---|
| **id** | DPO-C4 |
| **title** | Axis B：合并重复 FE client `putProductPrintMapping` / `saveProductPrintMapping` 为单一导出 |
| **owner** | Cursor |
| **axis** | B |
| **branch** | `cursor/dpo-c4-merge-print-put-client` |
| **in scope** | `web/src/api/client.js` 中两处同 URI PUT（约 L281 `putProductPrintMapping` 与 L302 `saveProductPrintMapping`）合并为一个规范名；全仓引用改一处；删重复 `printMappingContract` / `productPrintMapping` 重复定义若仍在 |
| **out of scope** | 行为变更；BE；修订语义 |
| **acceptance** | ① 仅保留一个 PUT helper；② 设计器与样例页均编译通过；③ vitest + 引用 grep 无旧名残留（或旧名 = 薄 alias 并标 @deprecated 且本卡删 alias） |
| **likely paths** | `web/src/api/client.js`、`PrintDesignerView.vue`、`ProductSampleView.vue`、相关 test |
| **deps** | 最好在 C3 之后或同批小心合并，避免双改冲突 |
| **test plan** | `npx vitest run` 打印相关；`npm run build` |

---

#### DPO-C5

| 字段 | 内容 |
|---|---|
| **id** | DPO-C5 |
| **title** | Axis B：设计器纸张/方向控件纳入同一 revision 守卫 |
| **owner** | Cursor |
| **axis** | B |
| **branch** | `cursor/dpo-c5-designer-paper-revision` |
| **in scope** | `PrintDesignerView` 上纸张 / 方向变更与字段映射一样走脏检查 + PUT `expected_revision`；未保存切换产品拦截（已有则复用） |
| **out of scope** | 多模板（L4）；PDF（L5）；持久化 BE |
| **acceptance** | ① 改纸张后未保存离开有拦截；② 保存 PUT 含 paper/orientation + expected_revision；③ 409 冲突壳复用；④ vitest |
| **likely paths** | `web/src/views/market/PrintDesignerView.vue`、`PrintDesignerView.test.js` |
| **deps** | C4 合并后更干净；可并行若不动 client 签名 |
| **test plan** | vitest：纸张变更 → dirty；PUT payload |

---

#### DPO-C6

| 字段 | 内容 |
|---|---|
| **id** | DPO-C6 |
| **title** | Axis C：运营页加深 — 从一个**已有 list API** 增补一块诚实指标面板（不发明时间序列） |
| **owner** | Cursor |
| **axis** | C 星市/运营 |
| **branch** | `cursor/dpo-c6-ops-metric-panel` |
| **in scope** | `OperationsAnalyticsView` 增加一块面板：例如安装数/定义数/列表长度汇总或分布，**数据仅来自现有 list/summary API**；缺失时显示「暂无 / 未知」，**禁止**假图表编造 series |
| **out of scope** | 新 BE 聚合口；支付；H5；重做 XB1–XB5 壳；升级假 DONE |
| **acceptance** | ① 面板数据可追溯到现有 API 字段；② 空列表诚实空态；③ 无 Chart 假数据；④ vitest |
| **likely paths** | `web/src/views/market/OperationsAnalyticsView.vue`、`OperationsAnalyticsView.test.js`、`web/src/api/client.js`（只读已有方法） |
| **deps** | 无（XB 已合） |
| **test plan** | vitest mock list；断言无伪造数组长度 |

---

### 4.2 Claude（BE-heavy）— DPO-L1…L6

---

#### DPO-L1

| 字段 | 内容 |
|---|---|
| **id** | DPO-L1 |
| **title** | Axis A：BE 在 `profile=prod` 且 `idp=keycloak` 时校验必需 env — fail-fast 清晰错误，**永不 log secret** |
| **owner** | Claude |
| **axis** | A |
| **branch** | `claude/dpo-l1-keycloak-env-guard` |
| **in scope** | 启动期守卫：缺 `NEXUS_IAM_KEYCLOAK_ISSUER` / `CLIENT_ID` / `CLIENT_SECRET` / `REDIRECT_URI` 等时失败并指出**变量名**；日志与异常消息只含「已配置/未配置」或变量名，**禁止**打印值；与 L12 / ProductionProfileGuard 风格对齐 |
| **out of scope** | 真 Keycloak 联调验收；改 FE（C1）；写真实 env 文件入仓 |
| **acceptance** | ① 缺变量 → 启动失败且消息含变量名不含值；② 单测用假值断言日志/消息无 secret 正文；③ prod+keycloak 全齐时可启动（测试用占位） |
| **likely paths** | `nexus-bootstrap` 或 IAM 配置类、`*Profile*Guard*`、相关 test |
| **deps** | 无 |
| **test plan** | Spring 上下文测试 / 单元：缺省矩阵；日志 sink 断言 |

---

#### DPO-L2

| 字段 | 内容 |
|---|---|
| **id** | DPO-L2 |
| **title** | Axis B：**Flyway 下一全局版本** — 打印映射持久化表（**纯迁移专卡**） |
| **owner** | Claude |
| **axis** | B |
| **branch** | `claude/dpo-l2-print-mapping-flyway` |
| **in scope** | **仅** `V707__...`（若合入时 V707 已被占用则顺延下一全局空号）：打印映射主表 + 历史/修订表（薄）；`outOfOrder` 保持 off；配套 schema 说明短注释 |
| **out of scope** | Service 切 JDBC（→ L3）；任何 FE；业务 API 行为变更 |
| **acceptance** | ① 迁移文件版本 = 扫库时下一空全局号（基线期望 **V707**）；② 空库可升；③ 无 Java 业务改动（或仅空 ops 禁止）；④ 与 X5 契约夹具对齐 |
| **likely paths** | `nexus-p*/src/main/resources/db/migration/V707__*.sql`（模块以现网 Flyway 归属为准，打印属 p4 则跟 p4/p1 既有惯例） |
| **deps** | 无；**挡住 L3** |
| **test plan** | Flyway IT 或既有 migration 烟测；X5 可随后 |

---

#### DPO-L3

| 字段 | 内容 |
|---|---|
| **id** | DPO-L3 |
| **title** | Axis B：`PrintMappingService` 从内存切 JDBC + 修订/历史 API 薄片 |
| **owner** | Claude |
| **axis** | B |
| **branch** | `claude/dpo-l3-print-mapping-jdbc` |
| **in scope** | 替换 `InMemoryPrintMappingStore` 为 JDBC；保留 revision / `expected_revision` → 409 语义；薄历史列表 API（只读近 N 条）；wire snake_case 与现契约一致 |
| **out of scope** | 迁移文件（L2）；多模板（L4）；FE |
| **acceptance** | ① 重启后映射仍在；② 修订冲突 409；③ 单测 + 可选 `@SpringBootTest`；④ 无 secret 日志 |
| **likely paths** | `PrintMappingService.java`、`InMemoryPrintMappingStore.java`、`PrintMappingController.java`、`PrintMapping*Test.java` |
| **deps** | **L2** |
| **test plan** | `mvn -pl nexus-p4-market -am test`；修订三态 |

---

#### DPO-L4

| 字段 | 内容 |
|---|---|
| **id** | DPO-L4 |
| **title** | Axis B：多模板元数据 API（list / create copy-of-builtin）— **无**完整拖拽引擎 |
| **owner** | Claude |
| **axis** | B |
| **branch** | `claude/dpo-l4-multi-template-meta` |
| **in scope** | 列出内置 + 已存模板元数据；`POST` 从 `builtin.approval-form.a4.v1` 复制出可改副本（名称/纸张元数据级）；不实现自由拖拽排版 |
| **out of scope** | 拖拽画布；PDF；FE 设计器大改（可另开 Cursor 卡，本波未派则 later） |
| **acceptance** | ① list 含 builtin；② copy 产生新 id 且不改 builtin；③ 契约测；④ 文档一句 later=拖拽 |
| **likely paths** | `PrintMappingController` 或新 `PrintTemplate*`、resources/print、tests |
| **deps** | 建议 **L3** 后（持久化副本）；若仅内存可标临时但本波偏好 JDBC |
| **test plan** | 单元/契约：list/copy |

---

#### DPO-L5

| 字段 | 内容 |
|---|---|
| **id** | DPO-L5 |
| **title** | Axis B：真数据预览 payload BE 薄片（**优先**；本卡不并行做 PDF） |
| **owner** | Claude |
| **axis** | B |
| **branch** | `claude/dpo-l5-print-preview-payload` |
| **in scope** | 只读预览接口：在映射 + **样例/可选真实申请单 id** 上填充字段值 payload（租户边界诚实；无单则空值+说明）；供现有 HTML 预览消费 |
| **out of scope** | PDF 二进制（标 later）；拖拽；改 FE 大页（若需接线另卡） |
| **acceptance** | ① 无真实数据时值可空且说明「未绑定真实单据」；② 有样例 id 时按映射填值；③ 租户隔离测；④ 不引入支付字段 |
| **likely paths** | `PrintMapping*` / sample preview controller、tests |
| **deps** | L3 更佳；可先基于现内存+样例但须诚实标注 source |
| **test plan** | 单测：空单 / 样例单；权限否定 |

---

#### DPO-L6

| 字段 | 内容 |
|---|---|
| **id** | DPO-L6 |
| **title** | Axis C：升级步骤持久化 BE — 仅当真实运维存在时 UNKNOWN→DONE；否则保持 UNKNOWN |
| **owner** | Claude |
| **axis** | C |
| **branch** | `claude/dpo-l6-upgrade-step-persist` |
| **in scope** | 若需表结构 → **依赖 schema 专卡**（见 DPO-X5 备选或本波追加 schema-only；**不可**在本卡夹带迁移+行为）；记录步骤状态 API：默认 UNKNOWN；**仅**在明确运维回调/人工受控动作后标 DONE；禁止定时假绿 |
| **out of scope** | 假编排；支付；H5；改 XB FE 壳（C6 除外的运营加深） |
| **acceptance** | ① 无运维事件 → 全 UNKNOWN + `recorded=false` 语义保持；② 有受控记录 → 对应步 DONE；③ `MarketUpgradeHonestyContractTest` 仍绿或扩展；④ 无 DONE 捷径单测 |
| **likely paths** | upgrade steps/facts services、`MarketUpgradeHonestyContractTest`、`MarketUpgradeStepsTest` |
| **deps** | schema 若缺 → **先** X5 schema-only 或独立 V70x 卡；与 L2 **版本号协调**（谁先谁占下一号） |
| **test plan** | honesty 契约；禁止 DONE 无记录 |

---

### 4.3 Codex（docs / scripts / tests）— DPO-X1…X6

---

#### DPO-X1

| 字段 | 内容 |
|---|---|
| **id** | DPO-X1 |
| **title** | Axis A：docker-compose Keycloak（+ realm import **stub**，无真实 secret）+ README 小节 |
| **owner** | Codex |
| **axis** | A |
| **branch** | `codex/dpo-x1-keycloak-compose` |
| **in scope** | 开发用 compose：Keycloak + 可选 Postgres；realm/client **占位** import；README：如何启动、如何填 `REPLACE_*`、**明确非演示机验收** |
| **out of scope** | 演示机部署；真实 secret 入库；宣称 M7 完成 |
| **acceptance** | ① `docker compose` 文件可启动（文档注明端口）；② 仓库无真实口令；③ README 链到 M7 / runbook |
| **likely paths** | `docker-compose*.yml`、`deploy/keycloak/**`、`README.md` 或 `docs/engineering/**` |
| **deps** | 无 |
| **test plan** | 文档审阅；可选 compose config 校验；CI 不强制拉镜像 |

---

#### DPO-X2

| 字段 | 内容 |
|---|---|
| **id** | DPO-X2 |
| **title** | Axis A：更新 M7 清单 + runbook 交叉链接；外部阻塞写清楚 |
| **owner** | Codex |
| **axis** | A |
| **branch** | `codex/dpo-x2-m7-runbook-links` |
| **in scope** | 修订 M7：链到 X1 compose、L1 守卫、C1/C2 FE、可跳过 smoke（X3）；重申外部阻塞表；runbook 增加「预备 vs 已验收」状态句 |
| **out of scope** | 勾选「已验收 Keycloak」；改产品代码 |
| **acceptance** | ① M7 仍标明未对真机验收；② 双向链接完整；③ 无 secret 样例实值 |
| **likely paths** | `docs/planning/M7-Keycloak演示验收清单.md`、`docs/engineering/runbooks/demo-host-prod-deployment.md` |
| **deps** | 可与 X1 同批或紧后 |
| **test plan** | docs-only 审阅 |

---

#### DPO-X3

| 字段 | 内容 |
|---|---|
| **id** | DPO-X3 |
| **title** | Axis A：可跳过 Keycloak 路径 smoke 脚本/CI — 无主机时 **exit 0 skipped** |
| **owner** | Codex |
| **axis** | A |
| **branch** | `codex/dpo-x3-keycloak-smoke-skippable` |
| **in scope** | 脚本检测 `NEXUS_DEMO_KEYCLOAK_SMOKE_URL`（或同类）未设 → 打印 skipped 并以 0 退出；已设则跑最小 HTTPS 探活/登录重定向检查（不写 secret 到日志） |
| **out of scope** | 强制 CI 连真机；存储凭据 |
| **acceptance** | ① 默认 CI 绿（skipped）；② 文档说明启用变量；③ 失败时只报状态码/URL host |
| **likely paths** | `scripts/**`、`.github/workflows/**`（若有） |
| **deps** | X1/X2 文案；不阻塞 |
| **test plan** | 本地无 env 跑脚本 → 0；有假 URL 时行为符合文档 |

---

#### DPO-X4

| 字段 | 内容 |
|---|---|
| **id** | DPO-X4 |
| **title** | Axis B：SpringBootTest / 契约测 — 捕获 ObjectMapper 启动回归（#647 类） |
| **owner** | Codex |
| **axis** | B |
| **branch** | `codex/dpo-x4-print-mapper-boot-test` |
| **in scope** | 增加能拉起与打印映射相关的 Spring 上下文或 bootstrap 契约，确保 `PrintMappingService` 所需 `ObjectMapper` bean 在真实装配下可用（补 CF-RD5 §4.3 缺口） |
| **out of scope** | 改业务逻辑；FE |
| **acceptance** | ① 测试在缺 bean 配置时会红；② main 现状下绿；③ 命名清晰关联 #647 |
| **likely paths** | `nexus-p4-market/src/test/**`、`nexus-bootstrap/src/test/**` |
| **deps** | 无（可先于 L2） |
| **test plan** | `mvn -pl nexus-p4-market -am test` / bootstrap 指定 test |

---

#### DPO-X5

| 字段 | 内容 |
|---|---|
| **id** | DPO-X5 |
| **title** | Axis B/C：打印持久化 schema 契约测 + docs 夹具（L2 之后）；若 Claude 未接纯 schema，则本卡可改接 **升级步骤 schema-only Flyway** |
| **owner** | Codex |
| **axis** | B（主）/ C（备选 schema） |
| **branch** | `codex/dpo-x5-print-schema-contract` |
| **in scope** | **主路径**：L2 合入后，`PrintMappingSchemaContractTest`（或扩现有 schema 测）+ 短 docs 夹具列出表/列；**备选**：若升级步骤需要表且 L6 依赖，则本卡改为 **schema-only** 迁移（下一全局空号）+ 契约，供 L6 使用 |
| **out of scope** | 业务 Service；FE |
| **acceptance** | ① 契约锁定表名/关键列；② 与 L2 或升级 schema 文件一致；③ outOfOrder off |
| **likely paths** | `nexus-p4-market/src/test/**Schema*`、`docs/planning/*Schema*`、migration 目录 |
| **deps** | **L2**（主路径）或与 L6 协商版本号（备选） |
| **test plan** | schema contract 单测 |

---

#### DPO-X6

| 字段 | 内容 |
|---|---|
| **id** | DPO-X6 |
| **title** | Axis C：收口/对账 XB 规划 #566 + XB deepen 账本 stub；或扩展升级诚实契约 |
| **owner** | Codex |
| **axis** | C |
| **branch** | `codex/dpo-x6-xb566-reconcile` |
| **in scope** | ① 文档 PR 说明：建议 **merge #566**（规划已由 XB15 兑现）或 **close as superseded**（指向 XB15 账本 + 本 DPO）；② 新增 `docs/planning/DPO-XB加深账本_stub.md`（或本文件修订节）列出 C6/L6/X6 状态位；③ **或者**扩展 `MarketUpgradeHonestyContractTest` 覆盖新持久化边界（与 L6 协调，二选一写清 PR） |
| **out of scope** | 重做 XB1–5；支付；H5 |
| **acceptance** | ① #566 有明确处置建议文案（实际点 merge/close 需 Eric 权限时可只备正文）；② 账本 stub 存在；③ 若选契约扩展则测绿 |
| **likely paths** | `docs/planning/XB15-*`、新 stub、`MarketUpgradeHonestyContractTest.java` |
| **deps** | 本 DPO 规划合入后执行；契约扩展依赖 L6 |
| **test plan** | docs 审阅；或 mvn 指定 honesty test |

---

## 5. NexusOne 审查清单（**仅 review，无实现卡**）

NexusOne 在每张实现 PR 上按下列项勾选，**不编写功能代码**：

| 审查项 | 要点 |
|---|---|
| **范围** | 单卡单 PR；未越轴；未重做 XB1–5；未宣称 Keycloak 真机验收 |
| **Flyway** | 仅专卡；版本 = 下一全局空号（基线后 remotes 再扫）；`outOfOrder` off；无 UI 夹带 |
| **诚实性** | 升级步骤无假 DONE；运营面板无伪造 series；预览无真实数据时明示 |
| **Secrets** | 日志/FE/PR/compose 无真实 secret；失败消息仅变量名 |
| **FE/BE 拆分** | FE 卡无 BE 大改；BE 卡无无关 FE；迁移独立 |
| **硬禁** | 无 H5、无支付发明、无假绿 |
| **测试** | 验收中的 vitest/mvn/契约是否真实执行 |
| **依赖** | L3 是否误先于 L2；L6 是否缺 schema |

---

## 6. 建议并行队列

| 批 | Cursor | Claude | Codex |
|---|---|---|---|
| 1 | C1, C3 | L1, L2 | X1, X4 |
| 2 | C4, C5 | L3 | X2, X3 |
| 3 | C2, C6 | L4, L5 | X5（跟 L2） |
| 4 | — | L6（跟 schema） | X6 |

---

## 7. 工作树纪律

| 角色 | 前缀 | worktree |
|---|---|---|
| Cursor | `cursor/` | `D:\Projects\NexusOne-cursor` |
| Claude | `claude/` | `D:\Projects\NexusOne-claude` |
| Codex | `codex/` | `D:\Projects\NexusOne-codex` |
| NexusOne（本规划 / review） | `nexusone/` | `D:\Projects\NexusOne-nexusone` |

- **禁止**碰 `D:\Projects\NexusOne` 主克隆（除必要时 fetch-only）
- **禁止** junctions
- 实现卡禁止改本规划文件范围外的无关波次

---

## 8. 修订记录

- 2026-09-27：V0.1 初稿（docs-only）。Flyway max **V706**；18 张薄卡；NexusOne 仅审查。#566 仍 open，交 X6 收口建议。
