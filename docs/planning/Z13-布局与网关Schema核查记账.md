# Z13 · 布局与网关 schema 核查

结论：现有列足够，无需新增 Flyway。未新增版本，outOfOrder 不变。

- 流程：V551 `business_meta.process_definition_draft.bpmn_xml text` 保存完整 XML；没有节点类型 enum/白名单列。Z11/Z12 已测试 exclusive/parallel XML 原样传入存储。
- 模板：V005 `platform.form_template_field.options jsonb` 可内嵌 `options.layout: {x,y,w,h}`；`form_template_version.fields_snapshot jsonb` 保留发布快照。schema contract 与 JDBC mock 测试覆盖写入参数、读取对象、发布快照。
- 注意：当前 FieldWrite/FieldView 不含顶层 layout。前端不能仅发顶层 layout 并宣称已持久化；Z4 应映射到 options.layout 或另外明确 DTO 契约。JSON 容器足够不等于任意未声明字段会自动保存。
- 本卡不改业务功能/UI，不连接真实库。测试为 schema 源文件与 JDBC mock 契约，非真实 PostgreSQL 往返验收。

验证：Maven reactor `-pl nexus-p3-studio -am -Dtest=ProcessDefinitionDraftSchemaContractTest,FormTemplateDraftServiceJdbcBindingTest -Dsurefire.failIfNoSpecifiedTests=false test`；依赖与临时产物位于本 worktree `.local/`。
