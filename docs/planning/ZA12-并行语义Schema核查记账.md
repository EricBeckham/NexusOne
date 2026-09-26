# ZA12 并行语义 / ZA 波 Schema 核查记账（无需迁移）

- 卡片：`docs/planning/WEB自由画布加深任务卡_ZA波_V0.1.md` · ZA12（Codex 队列，**由 Claude 代 Codex 完成**）
- 基线：`main`（ZA11 #561 合入后）；前序核查：`docs/planning/Z13-布局与网关Schema核查记账.md`
- 结论：**ZA 波不需要 Flyway 迁移**；未新增迁移文件，全局最高版本仍为 **V705**（全仓 35 个迁移），`outOfOrder` 未触碰。

## 触发点逐项核查

| 触发 | 数据落在哪里 | 是否需要新列 / 新表 |
|---|---|---|
| ZA11 并行 fork/join 配对校验 | 结构本身在草稿 `business_meta.process_definition_draft.bpmn_xml`（`text not null`，V551）；校验结果每次请求现算，按既有 `ProcessDefinitionValidationResult.ValidationError(code, message, nodeId, flowId)` 返回，**不落库** | 否 |
| ZA7–ZA9 条件 DSL | `<conditionExpression>` 在同一 `bpmn_xml` 文本内；结构化编辑只是前端互转 | 否 |
| ZA4 吸附 | Cursor ZA4（#556）：仅常量，无持久化 | 否 |
| 表单自由布局（Z4，前序） | `options` / `fields_snapshot` jsonb（V005），Z13 已核 | 否 |

## 契约测试（防回退）

`nexus-p3-studio/src/test/java/one/nexus/p3/flyway/ParallelSemanticsSchemaContractTest.java`：

1. `ValidationError` 的记录分量恰为 `code, message, nodeId, flowId`——ZA11 新错误码复用既有定位字段；若将来加字段，属于 API 变更而非 schema 变更。
2. `JdbcProcessDefinitionDraftStore.save` 把含 fork/join 与条件的 XML **原样**绑定到 `bpmn_xml`，写入参数仍是 id / tenant / key / name / bpmn_xml / actor 六个。
3. 两个迁移根（`nexus-p3-studio`、`nexus-bootstrap`）的所有迁移都不出现 `gateway*` / `parallel*` / `fork_*` / `pairing*` / `snap_*` / `validation_error*` / `condition_dsl*` 之类存储。

与 Z13 的 `ProcessDefinitionDraftSchemaContractTest`（`bpmn_xml text not null`、无 `gateway_type` 列、表租户隔离）一起通过：`mvn -pl nexus-p3-studio -am test -Dtest=ParallelSemanticsSchemaContractTest,ProcessDefinitionDraftSchemaContractTest` → 3 + 2 tests, 0 failures。

## 若将来确需迁移

仅由专门的 schema 卡新增；取下一全局版本（当前应为 **V706**）；`outOfOrder` 保持关闭；不得夹带功能 UI。本卡未触发这些条件。
