# XB15 XB 波打勾记账 / 星市缺口收口（docs-only）

- 卡片：`docs/planning/WEB星市缺口收口任务卡_XB波_V0.1.md` · XB1–XB15（规划 PR #566 记账时仍为 open，卡面按其分支内容对照）
- 分工：Cursor XB1–XB5（运营分析 FE + 升级 stepper 壳）；Claude XB6–XB15（升级读口、权益投影读模型、schema 核查、契约测、记账）。本波未派 Codex。
- 记账基线：`main@16a3e11b`（XB14 合入后）

## 1. 打勾

| 卡 | 验收要点 | 状态 | PR |
|---|---|---|---|
| XB1 | `/p4/operations` 运营分析页壳 | ✅ | [#567](https://github.com/EricBeckham/NexusOne/pull/567) |
| XB2 | `p4Nav.operations` 指向 `/p4/operations` | ✅ | [#568](https://github.com/EricBeckham/NexusOne/pull/568) |
| XB3 | 运营轻量计数，来自既有 list API | ✅ | [#569](https://github.com/EricBeckham/NexusOne/pull/569) |
| XB4 | 升级 stepper 诚实 UNKNOWN 壳（不假绿） | ✅ | [#570](https://github.com/EricBeckham/NexusOne/pull/570) |
| XB5 | 运营页空 / 错 / 表头打磨 + 薄测 | ✅ | [#571](https://github.com/EricBeckham/NexusOne/pull/571) |
| XB6 | `GET /api/v1/p4/installations/{id}/upgrade-steps`：六步目录（预检 · 依赖检查 · 结构扩展与迁移 · 配置 · 切换 · 验证），`recorded = false`，**全部 UNKNOWN**，附说明「升级步骤没有被记录…」；租户隔离（缺失 / 他租户 404） | ✅ | [#572](https://github.com/EricBeckham/NexusOne/pull/572) |
| XB7 | `GET …/upgrade-facts`：兼容性 / 结构变更 / 数据迁移 `value = null` + 说明；回滚「仅切换版本」+「数据不会随之回滚」 | ✅ | [#573](https://github.com/EricBeckham/NexusOne/pull/573) |
| XB8 | 已装产品 stepper 展开时读 XB6 / XB7；状态只认 DONE / ACTIVE / FAILED / UNKNOWN，其余按 UNKNOWN；facts 空值显示「暂无」；读失败显示「升级信息暂时无法读取…」+ 重试 | ✅ | [#574](https://github.com/EricBeckham/NexusOne/pull/574) |
| XB9 | 权益投影读模型盘点：复用 M20 + V100，无需新列（`docs/planning/XB9-权益投影读模型盘点.md`） | ✅ | [#575](https://github.com/EricBeckham/NexusOne/pull/575) |
| XB10 | M20 视图加按订阅 `summary`（`enabled_count / total_count / projected / projection_version / projected_at`）；`projected` 仅当 V100 投影 `source = SUBSCRIPTION` 且指向本订阅为 true；投影读不到为未知（null）；无价格字段 | ✅ | [#576](https://github.com/EricBeckham/NexusOne/pull/576) |
| XB11 | 订阅表「权益状态」列接 XB10：「N/M 项启用」+「当前生效 / 未生效」，未知不打标签，无数据「暂无」；删除字面量「暂无投影读模型」 | ✅ | [#578](https://github.com/EricBeckham/NexusOne/pull/578) |
| XB12 | Schema 核查：**无需迁移**；`XbWaveSchemaContractTest` + `docs/planning/XB12-星市缺口Schema核查记账.md` | ✅ | [#579](https://github.com/EricBeckham/NexusOne/pull/579) |
| XB13 | 升级 UNKNOWN 诚实契约：`MarketUpgradeHonestyContractTest`（无 DONE 路径、facts 不编造）、`MarketUpgradeWireNamingTest`（产品 mapper，null 缺省）、web HTTP 契约 | ✅ | [#580](https://github.com/EricBeckham/NexusOne/pull/580) |
| XB14 | 投影读模型契约：`CommercialEntitlementSummaryContractTest`（形状、可空 `projected`、无金钱字段、路径）、`CommercialEntitlementWireNamingTest`（snake_case；未知 `projected` 缺省而非 false）、web HTTP 契约；订阅表「未知」样例改为真实线上形状 | ✅ | [#581](https://github.com/EricBeckham/NexusOne/pull/581) |
| XB15 | 本记账 | ✅ | 本 PR |

硬禁核对：无 H5（未触 `web/src/mobile/**`）；无假绿 stepper（后端无任何产出 DONE 的路径，XB13 钉住）；无支付 / 计费 / 价格字段（XB12、XB14 钉住）；无高级 BPM；无新迁移。

## 2. 三个缺口是否收口

| 缺口 | 收口前 | 现在 | 结论 |
|---|---|---|---|
| 运营分析 | 导航无落点 | `/p4/operations` 页 + 来自既有 list API 的轻量计数（Cursor XB1–XB5） | 已收口（薄版） |
| 升级 stepper | 前端写死占位 | 前端读真实后端 XB6 / XB7；后端如实返回「未记录」 | **接线已收口；步骤本身仍 UNKNOWN**（见 §4） |
| 权益投影 | 订阅表写死「暂无投影读模型」 | 按订阅摘要 + 是否为租户当前生效投影 | 已收口 |

## 3. 验证摘录

- `mvn -B -pl nexus-p4-market -am test`（`main@16a3e11b`）：nexus-p4-market **Tests run: 36, Failures: 0, Errors: 0**（含 `XbWaveSchemaContractTest` 4）。
- `mvn -pl nexus-bootstrap -am test -Dtest=MarketUpgradeWireNamingTest` / `CommercialEntitlementWireNamingTest`：各 1 test，0 failures（XB13 / XB14 PR）。
- `cd web && npx vitest run`：XB14 合入时 **132 files / 548 tests passed**；`npm run build` 通过。
- 本地实机（Eric-Desktop，一次性库 + stub 登录 林晓，demo profile）：
  - XB8：已装产品展开 → 六步全部「暂无」+ 未记录说明；facts 三项「暂无」、回滚「仅切换版本」。
  - XB10：订阅开通后 `summary = {enabled_count: 1, total_count: 1, projected: true, projection_version: 2}`。
  - XB11：订阅表该行显示「1/1 项启用 · 当前生效」。
  - 截图工具多次超时，版面以 DOM 读取记录于各 PR。

## 4. 仍为 UNKNOWN / 未知的项（诚实，不是缺数据）

| 项 | 为什么 | 要变成真值需要 |
|---|---|---|
| 升级六步状态 | 当前升级只切换 `installation.version_id`，不执行也不记录预检→验证 | 真升级编排 + 步骤持久化（schema 专卡，取 **V706**，`outOfOrder` 关闭） |
| 兼容性 / 结构变更 / 数据迁移 facts | `app_version` 只有 release notes，没有任何声明这些事实的字段 | 版本发布时声明并存储（同上，另开卡） |
| `projected` 为未知 | 投影读失败时不猜 true / false，线上缺省该键 | — |
| 多条 ACTIVE 订阅中非最后开通者 `projected = false` | V100 一租户一行，最后开通覆盖 | 这是真实状态；改按订阅多行投影属权益模型变更，另开卡 |

## 5. 交界 / 后续

| 事项 | 状态 |
|---|---|
| 规划 PR #566 | 记账时仍 open。 |
| 升级编排 / 回滚数据 | 未开卡；回滚现为「仅切换版本，数据不回滚」，已在 facts 中如实说明。 |
| 支付 / 计费 | 本波硬禁，未涉及；P0 去支付路径另有 PR（#582），与本波无交集。 |