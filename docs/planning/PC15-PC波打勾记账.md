# PC15 PC 波打勾记账 / 平台配置完整感（docs-only）

- 卡片：`docs/planning/WEB平台配置完整感任务卡_PC波_V0.1.md`（规划 #577 已合）
- 分工：Cursor PC1–PC5（向导壳 · 导航 · 空态 · 绑定薄面 · 入口文案）；Claude PC6–PC15（就绪态 · 三样例 BE/FE · 契约 · 记账）。本波未派 Codex。
- 记账基线：`main@cd5b977c`（PC14 合入后）

## 1. 打勾

| 卡 | 验收要点 | 状态 | PR |
|---|---|---|---|
| PC1 | `/p4/product-config-wizard` 向导壳，四步固定文案，默认不打勾 | ✅ | [#584](https://github.com/EricBeckham/NexusOne/pull/584) |
| PC2 | 导航 / 产品定义页入口，与表单模板、流程设计器、产品定义互链 | ✅ | [#586](https://github.com/EricBeckham/NexusOne/pull/586) |
| PC3 | 向导空态 / 错误态走 S 反馈壳 | ✅ | [#587](https://github.com/EricBeckham/NexusOne/pull/587) |
| PC4 | 向导内 SKU 绑定薄面板（复用绑定 API） | ✅ | [#589](https://github.com/EricBeckham/NexusOne/pull/589) |
| PC5 | 表单模板入口文案 / 向导链接 / 截图路径 | ✅ | [#590](https://github.com/EricBeckham/NexusOne/pull/590) |
| PC6 | `GET /api/v1/p4/product-definitions/{id}/config-readiness`：form / process / sku / publish 四步 `DONE / NOT_READY / UNKNOWN`，只由已存数据判定；`complete` 仅全 DONE 为真 | ✅ | [#588](https://github.com/EricBeckham/NexusOne/pull/588) |
| PC7 | 向导「当前产品」下拉（`?definition=`）读 PC6 真态；未知词 / 读失败保持待开始 + 重试 | ✅ | [#592](https://github.com/EricBeckham/NexusOne/pull/592) |
| PC8 | `/p4/product-samples/ledger`：产品表单字段为列的只读样例台账，一行「暂无记录」+ 说明 | ✅ | [#602](https://github.com/EricBeckham/NexusOne/pull/602) |
| PC9 | `GET …/{id}/samples/ledger`：列来自绑定的已发布表单版本，`rows` 恒空 | ✅ | [#597](https://github.com/EricBeckham/NexusOne/pull/597) |
| PC10 | `/p4/product-samples/print`：A4 纵向纸面预览，标签 + 空白线，无设计器控件 | ✅ | [#624](https://github.com/EricBeckham/NexusOne/pull/624) |
| PC11 | `GET …/{id}/samples/print`：代码内置版式 `builtin.approval-form.a4.v1`，单据信息 / 申请内容 / 审批（仅绑流程时）；零迁移 | ✅ | [#620](https://github.com/EricBeckham/NexusOne/pull/620)（前置修复 [#612](https://github.com/EricBeckham/NexusOne/pull/612)） |
| PC12 | `/p4/product-samples/report`：四个计数卡，null / 缺键显示「暂无」不显示 0；趋势只一行「趋势暂无」，无图 | ✅ | [#636](https://github.com/EricBeckham/NexusOne/pull/636) |
| PC13 | `GET …/{id}/samples/report`：表单字段 / 绑定 SKU / 生效订阅 / 已开通租户；统计失败为 null；`trend_available` 恒 false | ✅ | [#635](https://github.com/EricBeckham/NexusOne/pull/635) |
| PC14 | `PcWaveContractTest`、`ProductConfigSamplesWireNamingTest`、web HTTP 契约；顺带修 `PlatformBindingWireNamingTest` 编译 | ✅ | [#638](https://github.com/EricBeckham/NexusOne/pull/638) |
| PC15 | 本记账 | ✅ | 本 PR |

执行顺序说明：三对样例都是**先 BE 后 FE**合入（PC9→PC8、PC11→PC10、PC13→PC12），FE 直接消费真契约；仍一卡一 PR。

## 2. 完整感结论

| 问题 | 结论 |
|---|---|
| 向导能否跟完 | **能**。演示种子产品「演示数据 · 请假报销」四步真实全 DONE（表单已发布、流程 demoSimpleApproval v1 已发布、已绑 demo.simple-approval、应用有已发布版本）；新建草稿产品只显示「配表单 已完成」，其余「未就绪」+ 后端原因。 |
| 三样例能否打开 | **能**。向导「当前产品」条右侧「样例：台账 / 打印 / 统计」三个入口，带同一 `definition`；三页都有产品切换、空态、错误重试。 |
| 是否开迁移 | **否**。PC 波全部零迁移（只读现表 V005 / V600 / V601 / V705 + 代码常量）。全局最高现为 **V706**，来自 P2 #601，不属于 PC。 |
| 与 XB 路径冲突 | **零**。未改 `/p4/operations`、升级 stepper / upgrade-steps / facts、订阅表投影列、`CommercialEntitlements*`。 |
| 硬禁 | 无 H5（未触 `web/src/mobile/**`）；无支付 / 计费字段（契约测扫描）；无假 KPI / 假趋势（统计只有当前计数，`trend_available=false`，页面无图）；无平台审批订阅改动；无高级 BPM。 |

## 3. 验证摘录

- `mvn -B -pl nexus-p4-market -am test`：nexus-p4-market 58 tests（PC13 时）、0 failures；PC14 新增 4 + bootstrap wire 2 通过。
- `cd web && npx vitest run`：PC14 时 **140 files / 599 tests passed**；`npm run build` 通过。
- 本地实机（一次性库 + demo profile，stub 登录 林晓，1280×800）：
  - PC7：种子产品四步 DONE；「PC7 草稿产品」`DONE 配表单`、其余 `NOT_READY`。
  - PC8：表头「事由* 文本 / 天数 数字 / 金额 金额 / 审批人* 人员」，表体「暂无记录」。
  - PC10：A4 纸 560×792（1.414），三节，不溢出；截图目检一次成功。
  - PC12/13：`4 / 1 / 0 / 0`（与 psql 直查一致）；以演示租户开通后 `4 / 1 / 1 / 1`。
  - 截图工具多次超时，其余版面以 DOM 量测记录于各 PR。

## 4. 遗留 / 交界

| 事项 | 状态 |
|---|---|
| `nexus-p3-studio` 16 个 Postgres IT 在 main 上报错 | #601 的 V706（放在 `nexus-p1-tenant`）引用 `market` schema，p3 IT 不加载 p4 迁移。已在 #612 说明，另起任务，未在 PC 卡私改迁移。 |
| main 编译中断两次 | #601 多一个 `)`（#612 修）；#631 改 `TemplateView` 未改 bootstrap 测试（#638 修）。 |
| 打印设计器 | PC 只有内置样例预览；CF-R4（#629）已在同页标「完整打印设计器 · later」。设计器壳由 CF-RD 系列接续。 |
| 统计趋势 | 无时间序列聚合；需要时另开卡，届时 `trend_available` 才可能为 true。 |
| 产品定义「发布」 | 定义本身无发布动作，向导「发布」按「绑定 SKU 所属应用有已发布版本（已在星市上架）」判定，`definition_status` 原样返回。 |