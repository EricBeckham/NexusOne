# DL-X2 · Prod-safe 演示内容手册

> **预备 ≠ 验收。禁止在 prod 推荐或开启 `NEXUS_DEMO_ENABLED=true` 及等价自动 seed/demo-context 绕过。** 真机保持 demo flags OFF；内容通过强鉴权手工配置或已合入的受控 API 准备，不复制本机种子/口令。

先完成 [真机 runbook](demo-host-realhost-launch.md) 与 [M7](../../planning/M7-Keycloak演示验收清单.md)。主机、IdP、vault、真实账号权限不足时停止，不能通过放开 demo 开关跳过。

## 手工最小内容（顺序执行，逐项读回）

| 步骤 | 操作 | 读回与停止条件 |
|---|---|---|
| 1 tenant | 具名平台管理员在星域新建专用演示租户；已有同用途租户则读回复用，不重复创建 | 租户详情/启用状态符合演示；权限403先解决授权，不借用stub header |
| 2 identities | IdP 管理员开通发起人A、审批人B；IAM 管理员建立两人的同租户 membership/最小职责 | 两账号分别Keycloak登录并建立该租户context；未映射身份即停止；凭据不入仓/会话 |
| 3 form | 平台表单模板新建最小申请字段，保存→校验→发布 | 发布版本真实存在；必填/审批人字段可读，失败不标完成 |
| 4 flow | 星构流程设计器准备开始→审批用户任务→结束；审批人引用与本租户B一致，保存→validate→publish | 实际发布版本可用；不以草稿valid代替部署，不需高级BPM |
| 5 product | 星市产品定义绑定已发布表单版本与上述流程，核产品配置就绪 | 读`GET /api/v1/p4/product-definitions/{id}/config-readiness`；缺项先配置，不手改数据库标绿 |
| 6 catalog | 使用管理员已准备的真实目录SKU，将产品定义绑定目录SKU | 没有可用SKU/发布目录时记“目录内容缺失”卡点，交管理员/已合bootstrap；不要假设本地demo商品会自动存在 |
| 7 activation | A在目录发起开通申请；有审批要求时平台管理员在申请列表按受控流程批准 | 安装/权限状态读回；空列表只是尚未申请或授权不符，不是系统已自动seed |
| 8 application | A填写申请→B待办填写意见并处理→A我的申请核对状态与意见 | 记录脱敏事实与断点；空待办检查租户/审批人/流程，不生成假审批结果 |

可核对的现有 API：租户 `POST /api/v1/p1/tenants`；表单 `POST /api/v1/p1/platform/form-templates` 与 `/{id}/validate`、`/{id}/publish`；产品定义 `/api/v1/p4/product-definitions` 与 `PUT /{id}/skus/{skuCode}`。本文不提供带token的curl/HTTP样例，实际字段按对应已合Controller/产品页面校验，不能猜请求体。

## 受控 bootstrap / 按步准备的交界

DL-L1（prod-safe bootstrap）/ DL-L4（缺项清单、apply-one-step）见 [DL 规划 §4.2](../../planning/WEB上线演示任务卡_DL波_V0.1.md)。本文编写时不假定 API 已合或 URI 已定；使用前必须核实已合代码、强平台鉴权、demo flags fail-fast、幂等读回与审计。只有变量名/门闩语义进入文档，不填任何真实值。不用一键准备把升级步骤改为DONE；无运维记录继续UNKNOWN。

## 空态与重复演示

DL-C4 的空/错误壳见 [DL-C4 任务定义](../../planning/WEB上线演示任务卡_DL波_V0.1.md)：空开通列表先执行步骤7，403检查职责/context，网络错查依赖；禁止通过启动自动seed“消除”空态。重复准备先按实体标识读回，有已存在内容就复用；清理另按受控remoke纪律，不调用本地reset。

证据仅记发布SHA、步骤、显示名、实体标识、脱敏错误类别；不记录 Authorization、cookie、token、口令或secret。手册存在不等于上述真机路径已执行。
