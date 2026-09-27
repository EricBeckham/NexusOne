# 真实平台身份配置应用内容

普通部署的精确内容操作桥接不依赖 demo context、回环或额外 env 开关。仅接受有效 bearer + 服务端签发 context：必须属于配置的平台租户、有 person，且平台职责集合恰好是 platform_system_admin；写操作还检查既有 tenant.config:update，显式 DENY 优先。安全/审计管理员、普通租户成员、撤销/过期 context、仅演示头都不能替代授权。

只覆盖平台模板 create/update/validate/publish，产品定义 create/update/bind-SKU，应用包 create/version/review/approve/publish/unpublish，SKU create，以及开通审订 aggregate GET/approve/reject。AUTH6 的 GET-only 开关与测试边界不变；导入、任意 tenant 写入、权限授予、审计写入不在白名单内。mine=true 的开通申请列表继续走租户隔离，不能用于跳过写鉴权。

前端这些写口发送真实 Authorization 与 X-Nexus-Context-Id，不再 auth:false。演示头不参与桥接认证。操作的 actor 是当前 person，审计归属平台租户，不保存凭据。

路径：平台表单模板保存 → 校验 → 发布 → 产品定义绑定；应用包/SKU/版本页面完成目录准备；租户申请开通 → 平台审订。必须先正规合并/部署再在真实站点复验，不自动 seed 内容，不以空目录或测试绿代替演示机验收。

测试采用内存 IAM 与真实权限引擎，覆盖成功、缺凭据、无效/撤销 context、错误职责、显式 DENY、伪造 demo 头以及 mine 查询边界；保留 AUTH6 只读边界回归。无 Flyway、无演示机/env 修改。

## 尚未证明通过的后续链路

此修复是认证上下文桥接，不是跨租户安装授权，也不是流程目录改造。2026-09-27 代码复核发现：

- `MarketService.approveActivation` 以申请记录的目标租户安装，但 `JdbcMarketRepository.saveSubscription/saveInstallation/saveInstallationProductPin` 要求当前 kernel TenantContext 拥有该行；真实平台会话的租户不同。权限包 provision 同样要求当前 P2 TenantContext 对应目标租户。因此仅修复平台认证不能证明审订安装成功；不能构造假的目标租户 membership 或关闭隔离绕过。
- 产品定义绑定页通过 P3 当前租户的发布目录选流程；平台租户没有已发布流程时，租户侧已发布流程不会自然出现在此选择器。需要明确可绑定的发布流程归属/共享策略，不能把全部租户流程直接公开给平台目录或把 classpath 预览当成可运行发布版本。

这两项仍须独立安全/契约修复并验收。应用版本发布、SKU 创建与本批自动化测试均不意味着「订购→发起→审批→我的申请」已通。演示站此前保存的租户流程数据保持原样，本批未改站点、数据库或环境。
