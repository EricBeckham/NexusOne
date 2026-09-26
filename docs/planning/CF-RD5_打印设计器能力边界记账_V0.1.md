# CF-RD5 打印设计器能力边界记账（docs-only）

- 范围：CF-RD1 → CF-RD4（Claude），以及它们依赖或对齐的 PC10 / PC11、CF-R1 ~ CF-R4
- 记账基线：`main@8718d62f`（CF-RD4 合入后）
- 一句话结论：现在有一个**能打开、能绑定、能诚实保存、能跳到只读预览**的打印设计器**壳**。它基于一份内置版式，映射只存在服务器进程内存里。它**不是**排版引擎，也**不是**持久化的模板库。

## 1. 已交付

| 卡 | 交付 | PR |
|---|---|---|
| CF-RD1 | `/p4/product-samples/print/designer?definition=<id>`。三栏：左边槽位列表（按分区分组，标「系统属性」或字段类型）；中间 A4 画板，按分区分虚线区块，点槽位即选中；右边属性栏（槽位 / 分区 / 内容来源 / 字段）。数据来自 PC11 的内置样例版式。打印样例页有「打开设计器」入口。 | [#644](https://github.com/EricBeckham/NexusOne/pull/644) |
| CF-RD2-pre | CF-R3 合入后应用**无法启动**：`PrintMappingService` 需要一个不存在的 Jackson 2 `ObjectMapper` bean。改为私有 mapper 读 camelCase 契约。 | [#647](https://github.com/EricBeckham/NexusOne/pull/647) |
| CF-RD2 | 「字段 → 槽位」表，接 CF-R3 契约：<br>• 下拉只列 `accepts` 能接受该字段类型的槽位；<br>• 未映射的表单字段默认落入 `form.fields`；<br>• 非 repeatable 槽位被重复占用、或类型不符时，错误贴在字段上并禁止保存；<br>• 保存 = PUT 后立刻 GET，对比结果显示「读回一致 / 不一致」。 | [#650](https://github.com/EricBeckham/NexusOne/pull/650) |
| CF-RD3 | 映射带 `revision`：契约默认为 r0，每次被接受的 PUT +1，但只在进程内有效。PUT 可带 `expected_revision`，对不上返回 409 且不改任何内容。<br>设计器：<br>• 版本标签如实显示，并写明「重启丢失，不是持久版本号」；<br>• 成功或失败都用 S 壳 toast；<br>• 409 时显示冲突面板（载入服务器版本 / 明确用我的修改覆盖），**不会静默覆盖**；<br>• 其他失败保留草稿，行内显示原因和追踪号；<br>• 有未保存修改时禁止切换产品。 | [#653](https://github.com/EricBeckham/NexusOne/pull/653) |
| CF-RD4 | 「只读预览」跳到 CF-R2 在样例路由上的预览（`&from=designer`），不另建第二套预览：<br>• 有未保存修改时先说明「预览只显示已保存的 rN」，可以先保存再预览、仍然打开，或留在设计器；<br>• 预览页注明依据哪一版已保存映射，并提供「返回设计器」；<br>• 空选项从「不打印」改成「未映射（落入表单字段区）」，与契约语义一致。 | [#654](https://github.com/EricBeckham/NexusOne/pull/654) |

依赖与对齐：
- PC11 内置版式 [#620](https://github.com/EricBeckham/NexusOne/pull/620)；
- PC10 样例预览 [#624](https://github.com/EricBeckham/NexusOne/pull/624)；
- CF-R4 空错态与 later 文案 [#629](https://github.com/EricBeckham/NexusOne/pull/629)；
- CF-R3 映射契约 [#643](https://github.com/EricBeckham/NexusOne/pull/643)；
- CF-R1 映射表与纸张枚举 [#651](https://github.com/EricBeckham/NexusOne/pull/651)；
- CF-R2 只读 HTML 预览 [#652](https://github.com/EricBeckham/NexusOne/pull/652)。

迁移：**零**。全局最高仍为 V706（P2 #601），`outOfOrder` 未动。

## 2. 诚实边界（现在是什么，不是什么）

| 维度 | 现状 |
|---|---|
| 版式 | 只有一份内置版式 `builtin.approval-form.a4.v1`（单据信息 / 申请内容 / 审批），写在代码和 classpath JSON 里，不能新建或复制模板。 |
| 画板 | 按分区列出槽位，可以点选。**没有**拖拽、坐标、尺寸、对齐线、层级。 |
| 绑定 | 字段 → 契约槽位，一个字段只放一个槽位；未映射的字段一律落入 `form.fields`。**没有**「不打印」这个选项。 |
| 存储 | CF-R3 的 `InMemoryPrintMappingStore`，只在 JVM 内存里：重启后回到契约默认 r0；多实例之间不共享。 |
| 版本 | `revision` 是进程内修订号，不是持久版本号，也没有历史可以回看或回滚。 |
| 纸张 / 方向 | 枚举有 A4 / A5 / Letter，纵向 / 横向，在样例页（CF-R1）上修改；设计器只显示当前值。 |
| 预览 | CF-R2 的只读 HTML 预览，字段值**留空**，不套真实申请数据。 |
| 权限 | 与产品定义一样，要求平台 SYSTEM_ADMIN 身份。 |

## 3. later（本波不做，诚实列出）

| 能力 | 说明 / 前提 |
|---|---|
| 页眉页脚引擎 | 每页重复的页眉页脚、页码、Logo。需要分页模型。 |
| 跨页 / 分页 | 长表单或明细行跨页、分页符、表头重复。当前纸面固定一页，超出部分被裁掉。 |
| 拖拽排版与坐标 | 自由摆放、栅格吸附、尺寸调整。需要版式数据模型，会变更契约。 |
| 持久化与版本历史 | 映射落库，带真正的版本号、历史、回滚、审计。**需要迁移**：由专门的 schema 卡新增，取下一全局版本 **V707**，`outOfOrder` 保持关闭，不夹带 UI。 |
| 多模板 / 模板库 | 按产品维护多份打印模板，或复制内置模板后再修改。 |
| 真实数据打印 | 用一张真实申请单的值渲染（租户侧，涉及跨租户边界与字段权限）。 |
| PDF / 打印机输出 | 服务端生成 PDF、浏览器打印样式、批量打印。 |
| 富元素 | 条码 / 二维码、图片、签章、明细表格、条件显示。 |
| 设计器内改纸张 | 把 CF-R1 的纸张 / 方向选择搬进设计器，并受同一修订号保护。 |

## 4. 已知缺口（小，可单独开卡）

1. **样例页保存不带修订号**：CF-R1「保存映射」调用 PUT 时没有 `expected_revision`，会覆盖设计器刚保存的内容。修法是像设计器一样带上当前修订号，并处理 409。
2. **同一个 PUT 有两个 client 名字**：`p4.putProductPrintMapping`（CF-R1）和 `p4.saveProductPrintMapping`（CF-RD2）指向同一接口，可以合并为一个。
3. **`PrintMappingServiceTest` 没有覆盖 Spring 上下文**：#647 的启动故障能被 `@SpringBootTest` 类测试（如 `ProductConfigSamplesWireNamingTest`）捕获，但 CF-R3 自己的测试是纯单测。建议 schema / BE 卡都至少跑一次 bootstrap 测试。
4. **与本轴无关，但会让 main 上的全量测试变红**：`nexus-p3-studio` 有 16 个 Postgres IT，因 V706 引用 `market` schema 而失败（见 #612 说明），已另起任务。

## 5. 验证摘录

- `cd web && npx vitest run`：CF-RD4 合入时 **141 files / 625 tests passed**；`npm run build` 通过。
  - `PrintDesignerView.test.js` 覆盖 RD1 ~ RD4，共 20 个用例。
  - `ProductSampleView.test.js` 覆盖 RD4 的预览横幅。
- `mvn -B -pl nexus-p4-market -am test`：nexus-p4-market 69 tests，0 failures（含 `PrintMappingRevisionTest` 3、`PrintMappingServiceTest` 4）。
- 本地实机（一次性库 + demo profile，stub 登录 林晓，1280×900）：
  - RD1：左栏 10 个槽位；点「金额」→ 属性显示「申请内容 · 产品表单字段 · amount · 金额」。
  - RD2：读回 10 行默认映射；「天数」（NUMBER）只能选「未映射 / 表单字段区」；保存后读回一致，来源变为 `IN_MEMORY`。
  - RD3：
    1. 另一方先保存，服务器到 r1；
    2. 页面基于 r0 保存 → 冲突面板，服务器内容不变；
    3. 明确覆盖 → r2，toast「打印绑定已保存（进程内修订 r2）」。
  - RD4：有修改时提示「按 进程内修订 r2 生成」；选先保存再预览 → r3，预览横幅显示「修订 r3」，并滚动到纸面。
  - 截图工具偶尔超时，版面以 DOM 量测记录在各 PR。
