# 真实平台身份配置应用内容

普通部署的精确内容操作桥接不依赖 demo context、回环或额外 env 开关。仅接受有效 bearer + 服务端签发 context：必须属于配置的平台租户、有 person，且平台职责集合恰好是 platform_system_admin；写操作还检查既有 tenant.config:update，显式 DENY 优先。安全/审计管理员、普通租户成员、撤销/过期 context、仅演示头都不能替代授权。

只覆盖平台模板 create/update/validate/publish，产品定义 create/update/bind-SKU，应用包 create/version/review/approve/publish/unpublish，SKU create，以及开通审订 aggregate GET/approve/reject。AUTH6 的 GET-only 开关与测试边界不变；导入、任意 tenant 写入、权限授予、审计写入不在白名单内。mine=true 的开通申请列表继续走租户隔离，不能用于跳过写鉴权。

前端这些写口发送真实 Authorization 与 X-Nexus-Context-Id，不再 auth:false。演示头不参与桥接认证。操作的 actor 是当前 person，审计归属平台租户，不保存凭据。

路径：平台表单模板保存 → 校验 → 发布 → 产品定义绑定；应用包/SKU/版本页面完成目录准备；租户申请开通 → 平台审订。必须先正规合并/部署再在真实站点复验，不自动 seed 内容，不以空目录或测试绿代替演示机验收。

测试采用内存 IAM 与真实权限引擎，覆盖成功、缺凭据、无效/撤销 context、错误职责、显式 DENY、伪造 demo 头以及 mine 查询边界；保留 AUTH6 只读边界回归。无 Flyway、无演示机/env 修改。
