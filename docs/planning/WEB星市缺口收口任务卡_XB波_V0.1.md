# WEB 星市缺口收口任务卡（XB 波）V0.1

- 基线：`main`（ZA1–ZA15 已合；Eric「NexusOne next wave = 星市缺口收口 (XB波)」）
- 依据：L5 视觉差距清单记账项 + 第四波后置的 P4 `operations` later + 规范 §28.6 / §28.8；本波只收口**已盘点、可验收**的星市三缺口
- 规范：以仓库 `docs/design/星枢NexusOne_WEB页面设计与前端开发规范_V1.1.md` 为准；空态/错误/页眉对齐 O/Q/R/S token 与反馈壳
- 目标：三条缺口各开可验收薄片——① **运营分析** 去 later、页壳 + 诚实轻量表/计数；② **升级 stepper** 有后端则真状态、否则诚实 UNKNOWN（**禁假绿**）；③ **权益投影读模型** 订阅表不再写死「暂无投影读模型」，优先复用已有 M20/P1 投影，schema **仅证明必须时**
- 执行方：**Cursor / Claude** 二人并行；**不派 Codex 队列**（配额空）；NexusOne 协调
- 现状速记（2026-09-26 扫仓库确认）：
  - **运营分析**：`web/src/nav/p4.js` 中 `id: "operations"` 仍为 `later: true`、无 `to`；`web/src/router.js` **无** `/p4/operations`；规范 §28.8 要轻量「上架 / 订阅 / 安装 / 升级 / 使用趋势」，**不建**独立 BI；第四波曾后置「无聚合 API，避免假 KPI」
  - **升级 stepper**：`P4ManagementView.vue` 固定 `upgradeSteps = ["预检","依赖检查","结构扩展与迁移","配置","切换","验证"]`，每卡同一排字、无步骤状态；升级 facts 在 `devNotes` 下为「暂无数据」；BE `POST /installations/{id}/upgrade` 仅为版本切换（`MarketService.upgrade`），**无** Precheck→Verify 持久步骤态（L5 已记账）
  - **权益投影**：订阅表列硬编码「暂无投影读模型」（L5）；另有 M20 `CommercialEntitlementController` `GET /api/v1/p4/commercial-entitlements` + FE `CommercialEntitlementsView`；P1 已有 `platform.entitlement_projection`（V100）与 `GET /tenants/{id}/entitlement`；缺口是**订阅列表读模型接线 / 是否需新投影表**，不是从零发明计费
  - **明确不做 H5**：`web/src/mobile/**` 不在本波范围

## 默认纪律

1. **一卡一 PR**；标题以 `XB*` 开头；合并优先 **squash**。
2. 裸克隆 `D:\Projects\NexusOne` **只 fetch**；worktree 干活末尾。
3. **明确不做 / 硬禁**：
   - **H5** 与 `web/src/mobile/**`
   - **假绿 stepper**：无真实步骤态时不得把步骤画成成功/完成绿；只允许真实状态或诚实 **UNKNOWN** / 「暂无」
   - **发明支付 / 计费 / 优惠券 / 发票**；不改商业成交语义
   - **权益 schema 仅证明必须时**；优先复用 `commercial-entitlements` 与 `platform.entitlement_projection`
   - **高级 BPM**（会签/或签/加签转交/子流程）——本波无关
   - 独立 BI Designer、假 KPI、假趋势曲线
   - CI 复活宣称、真 Keycloak / 外网终态宣称
4. **视觉约束**：页壳/空态/错误走 O/Q/R/S token 与反馈壳；禁止另起视觉体系。
5. **薄片**：每卡 1–2 小时；一产品切片；**前端/后端能拆则拆**；**迁移 / 密钥单独成卡**；禁止一卡吞整运营 BI 或整升级引擎。
6. **Flyway**：仅证明必须时由专卡（XB12）新增；取**下一全局版本号**（当前仓最高仍 **V705** 规则：全局 +1）；`outOfOrder` **关闭**；优先证明现表/现 API 足够、无需迁移。
7. **验收**：本地打开对应页目检 + 截图进 PR；注明空态/UNKNOWN/错误态；有测试则跑相关单测并在 PR 摘录。

## 阶段对照

| 阶段 | 内容 | 本卡地位 |
|---|---|---|
| L5 / 第四波后置 | 升级 stepper 静态、订阅「暂无投影读模型」、`operations` later | 本波要对账收口 |
| M20 / M21 | 商业权益只读投影 API + 页 | **复用**，不重做 |
| P1 T2-A | `platform.entitlement_projection` + 租户权益页 | **复用** |
| **XB** | 星市三缺口：运营分析 · 升级 stepper · 权益投影读模型 | **本波开启** |
| （后） | 真升级编排引擎、BI、支付计费、H5 | 本波不开 |

## XB 波任务总表（XB1–XB15）

| 卡 | 标题 | 建议方 | 前置 | 估时 |
|---|---|---|---|---|
| **XB1** | 运营分析页壳：路由 `/p4/operations` + 空壳页（页眉/空态/骨架；无假 KPI） | Cursor | 无 | 1–2h |
| **XB2** | 侧栏去 later：`p4Nav.operations` 加 `to: "/p4/operations"`，去掉 `later: true` | Cursor | 建议 XB1 后（可同批紧随） | ~1h |
| **XB3** | 运营分析轻量表/计数接线：上架/订阅/安装等来自**已有** list API；失败/空诚实；**禁**假趋势 | Cursor | 建议 XB1/XB2 后 | 1–2h |
| **XB4** | 升级 stepper FE 诚实壳：步骤可渲染状态位；无数据 → UNKNOWN/「暂无」；**禁假绿** | Cursor | 无（可与运营分析并行意识上分开 PR） | 1–2h |
| **XB5** | 运营分析空态/错误态 + 页眉对齐 Q5/S；薄测；Cursor 轴收口前自检 | Cursor | 建议 XB3 后 | 1–2h |
| **XB6** | 升级步骤态 BE 读口（或诚实契约）：无持久步骤时返回 UNKNOWN 列表；**不**伪造 COMPLETED | Claude | 无 | 1–2h |
| **XB7** | 升级 facts BE 读口：兼容范围 / Schema / 迁移 / 回滚 —— null/UNKNOWN 诚实；禁「假装已检」 | Claude | 可与 XB6 并行或紧随 | 1–2h |
| **XB8** | Stepper FE 接 XB6/XB7：真实状态上色；UNKNOWN 保持中性；去掉开发味假数据依赖 | Claude | XB4 + XB6（建议 XB7）后 | 1–2h |
| **XB9** | 权益投影读模型盘点（docs 或短 PR）：对照 M20/`platform.entitlement_projection`/订阅表列；结论「复用 vs 需新列」 | Claude | 无 | 1–2h |
| **XB10** | 权益投影读模型 BE：订阅列表可取投影摘要（复用 commercial-entitlements 或薄 join）；**无支付字段** | Claude | 建议 XB9 后 | 1–2h |
| **XB11** | 订阅表 FE：去掉硬编码「暂无投影读模型」；有投影显示摘要/状态，无则诚实「暂无」 | Claude | XB10 后 | 1–2h |
| **XB12** | **Schema/契约专卡**：优先证明无需迁移 + contract；仅必须时 Flyway（V705 之上全局 +1；outOfOrder off） | Claude | XB9/XB10 触发时 | 1–2h |
| **XB13** | 契约测：升级步骤 UNKNOWN 诚实（无假绿路径）；相关 Java 或 HTTP 测 | Claude | 建议 XB6 后 | 1–2h |
| **XB14** | 契约测：权益投影读模型（list/get 或订阅列数据形状）；PR 摘录通过命令 | Claude | 建议 XB10 后 | 1–2h |
| **XB15** | XB 波打勾记账（docs-only）：XB1–XB15 勾选 + PR 链接 + 截图/摘录；三缺口收口结论 | Claude | 大部合入后 | 1h |

## 薄片要求

### Cursor XB1–XB5（运营分析 FE · stepper 诚实壳）

- **XB1** `web/src/router.js` 增 `/p4/operations`；新建薄页（建议 `web/src/views/market/OperationsAnalyticsView.vue` 或邻近命名）；`NxPageHeader` + 空态说明「轻量运营分析，非 BI」；加载可用骨架；**禁止**写死假数字/假折线。
- **XB2** 只改 `web/src/nav/p4.js`：`operations` 设 `to: "/p4/operations"`、删除 `later: true`；侧栏可点进 XB1 页；单测/快照若有 nav 断言则更新。
- **XB3** 页面展示轻量计数或稠密表：上架应用数、订阅数、安装数等——数据只来自已有 `p4` list/catalog/subscriptions/installations（或首页已用统计路径）；请求失败 → `NxErrorState`；空 → `NxEmpty` + 下一步；**禁止** invent 聚合服务、假「使用趋势」曲线（若无时间序列 API，用文案「趋势暂无读模型」代替假图）。
- **XB4** `P4ManagementView.vue`（及测试）：stepper 每步可带 `status: DONE | ACTIVE | FAILED | UNKNOWN`（或等价）；**默认 UNKNOWN**；样式上 UNKNOWN/未提供不得用成功绿；不本卡发明 BE。
- **XB5** 运营分析页空/错/成功三态目检 + 薄测；页眉/间距对齐已开通/商业权益页；PR 附截图；不扩 scope 到 BI。

### Claude XB6–XB15（stepper 真态 · 投影 BE/FE · 契约 · 清单）

- **XB6** BE 读口（如 `GET .../installations/{id}/upgrade-steps` 或挂在 installation 读模型上）：若仓库**无**步骤持久化，返回与 §28.6 对齐的步骤名 + 全员 `UNKNOWN`（或等价）；**禁止**为了好看返回全 `COMPLETED`；单测锁住「无状态 → 非绿」。
- **XB7** 只读 facts：兼容范围 / Schema 变更 / 数据迁移 / 回滚能力；无数据用 null/缺省，FE 显示「暂无」；禁止编造迁移已执行。
- **XB8** FE 消费 XB6/XB7：替换 XB4 的纯前端默认；有 DONE/ACTIVE/FAILED 才上对应色；UNKNOWN 中性；`devNotes` 假「暂无数据」块可收敛为正式只读区。
- **XB9** docs-only 或极短记账 PR：点名 `CommercialEntitlementService`、`platform.entitlement_projection`、`P4ManagementView` 订阅列、`MarketService.subscribeAndInstall` 投影路径；明确 XB10/XB12 走「复用」还是「新列」。
- **XB10** BE：让订阅列表或附属读口能给出投影摘要（enabled 数 / 关键态 / 指向 commercial-entitlement id 等）；**禁止**价格、账单、支付状态字段。
- **XB11** `P4ManagementView` 订阅表「权益状态」列接 XB10；有数据展示，无数据「暂无」；删除永远写死的「暂无投影读模型」字符串（测里断言）。
- **XB12** **仅本卡**可加 Flyway；优先 contract 证明 V100 投影表 + 现 subscription/sku 足够 → 记账「无需迁移」。若必须：下一全局版本、`outOfOrder` off、禁止夹带 UI。
- **XB13** 契约/单测：升级步骤在无编排引擎时不得出现假 COMPLETED；覆盖 XB6。
- **XB14** 契约/单测：投影 list/get 形状稳定；与 FE 列字段对齐；PR 摘录 `mvn -q -Dtest=... test` 或等价。
- **XB15** 新建 `docs/planning/XB15-XB波打勾记账.md`（或同目录约定名）：XB1–XB15 勾选 + PR + 截图路径；写明三缺口是否收口、是否开过迁移、遗留 UNKNOWN 是否诚实。

## 二人队列

| 人 | 顺序 |
|---|---|
| **Cursor** | **XB1 → XB2 → XB3 → XB4 → XB5**（一卡一 PR；**禁止中途停等 Eric 点 Run / Always Allow**——本批连续做完队列全部卡） |
| **Claude** | **XB6 → XB7 → XB8 → XB9 → XB10 → XB11 → XB12 → XB13 → XB14 → XB15**（XB8 依赖 XB4/XB6；XB11 依赖 XB10；XB12 按需优先「无需迁移」；一卡一 PR；同样连续跑完，不等 Run 点击） |
| **Codex** | **本波不派发**（配额空） |

## 延后（本波不做）

- H5 全文与 `web/src/mobile/**`
- 真升级编排引擎（异步 Job、回滚执行器、多租户灰度开关产品化）
- 独立 BI / 假 KPI / 假趋势
- 支付、计费、发票、优惠券
- 高级 BPM
- 打开 `outOfOrder=true`、手改 schema history

## 工作树路径

| 人 | 前缀 | worktree |
|---|---|---|
| Cursor | `cursor/` | `D:\Projects\NexusOne-cursor` |
| Claude | `claude/` | `D:\Projects\NexusOne-claude` |
| NexusOne | `nexusone/` | `D:\Projects\NexusOne-nexusone` |
| （参考）Codex | `codex/` | `D:\Projects\NexusOne-codex`（本波不用） |

### 本地执行纪律（Eric-Desktop · 强制）

1. 改动文件只落本 worktree；禁止写桌面/`Downloads`/裸克隆/他人目录。
2. 自己跑终端/构建/测试/git；**禁止等 Eric 点 Run / Always Allow**——批内连续做完队列全部卡，一卡一 PR，卡与卡之间不停顿等批准。
3. 自己开 PR / 能合则 squash 合 main；不是只交半截补丁。
4. 禁止 junction；裸克隆只 fetch。
5. 密钥/迁移：迁移只走 XB12；本波无密钥卡；发现密钥需求则停并记账，勿塞进功能卡。

## 修订记录

- 2026-09-26：XB 波初稿。Eric 选定「星市缺口收口」；对照 L5 + `p4Nav.operations` later + §28.6/§28.8 + M20/P1 投影现状；Cursor 5 卡 / Claude 10 卡；不派 Codex；不做 H5；禁假绿 stepper；禁发明支付；schema 仅证明必须。
