# NexusOne

**面向企业业务系统的模块化、多租户应用基础平台。**  
*A modular, multi-tenant foundation for enterprise applications.*

[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Build-Maven-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![Status](https://img.shields.io/badge/Status-Active%20Development-blue)](./docs/README.md)

> **项目状态：持续开发中。** 当前仓库包含模块实现、测试桩及阶段性验证证据，并不代表所有功能均已通过端到端或生产环境验收。生产使用前请自行进行安全与部署评估。

## 项目简介

NexusOne（星枢）探索如何以**模块化单体架构**复用企业应用的通用基础能力，减少不同业务系统在租户管理、身份与权限、数据访问、安全审计及业务扩展方面的重复建设。

仓库围绕**租户隔离、身份与访问管理、策略授权、业务工作流、事件处理与可审计性**组织代码，重视可测试的模块边界与明确的验证证据。项目仍在建设中，适合对企业级 Java 架构、多租户安全和模块化平台有兴趣的开发者阅读、讨论和贡献。

## 核心模块

| 模块 / 路径 | 职责范围 |
| --- | --- |
| `modules/isolation-kernel` | 租户隔离相关基础能力 |
| `nexus-p1-tenant` | 租户领域能力 |
| `nexus-p2-iam` | 身份与访问管理（IAM） |
| `nexus-permission-core` | 权限与策略能力 |
| `nexus-data-core` | 数据访问基础能力 |
| `nexus-crypto-core` | 密码学相关基础能力 |
| `nexus-audit-core` | 审计相关能力 |
| `nexus-event-core` / `nexus-task-core` | 事件与任务基础能力 |
| `nexus-cache-core` / `nexus-file-core` | 缓存与文件基础能力 |
| `nexus-notification-core` / `nexus-expression-core` | 通知与表达式能力 |
| `nexus-p3-studio` | 业务建模与 Studio 相关模块 |
| `nexus-p4-market` / `nexus-p5-connect` | 扩展市场与连接集成方向 |
| `nexus-bootstrap` | 应用装配与启动模块 |
| `web` | Web 前端相关代码 |

以上为模块**定位/代码组织**，并非全部功能均已发布或完成生产验收。

## 技术栈

- **后端：** Java 21、Spring Boot 4.1.1、Maven 多模块工程。
- **数据与集成：** 仓库包含 PostgreSQL、Flyway、Keycloak、Redis、S3 兼容对象存储等配置或集成相关代码；实际依赖取决于运行模式。
- **其他组件：** Flowable、Quartz、Bouncy Castle 等依赖见根目录 `pom.xml`。
- **前端：** `web/` 目录；具体环境与脚本以该目录配置为准。

## 快速开始（开发者）

**环境准备：** JDK 21、Maven，以及所选运行模式需要的数据库或外部服务。

```bash
git clone https://github.com/EricBeckham/NexusOne.git
cd NexusOne

# 参考示例配置，按实际环境设置变量；勿提交真实密钥
cp .env.example .env

# 编译与测试；外部依赖不可用时，部分集成测试可能无法完成
mvn test
```

> `.env` 文件不会自动被 Maven 或 Spring Boot 在所有环境中加载；请通过你的 shell、IDE 或启动配置显式提供所需环境变量。`NEXUS_P1_DEMO_CONTEXT_ENABLED` 等演示开关仅供本地测试，不应直接用于生产环境。应用启动流程与服务依赖请结合 [工程文档](./docs/README.md) 和相关模块说明核对。

## 安全与验证原则

NexusOne 涉及租户边界、认证授权与敏感数据，因此特别重视：

- **隔离优先：** 将跨租户访问、越权与权限提升视为重点风险。
- **证据可追溯：** 区分单元/契约测试、模拟环境验证和真实服务验证。
- **不夸大完成度：** `PASS`、`SKIP` 和待完成事项以对应文档为准，模拟测试通过不等于生产验收。
- **安全报告：** 请不要在公开 Issue 中披露未修复漏洞或真实凭据；可先通过仓库维护者的私有渠道联系。如配置了 GitHub Private Vulnerability Reporting，优先使用该功能。

## 文档与路线图

- [文档导航](./docs/README.md)
- [需求规格](./docs/requirements/星枢企业业务平台_需求规格_V1.1.md)
- [技术架构](./docs/architecture/星枢企业业务平台_技术框架_V1.1.md)
- [实施任务拆解](./docs/planning/实施任务拆解_V1.1.md)
- [团队领取任务清单](./docs/planning/团队领取任务清单_V1.1.md)
- [Issues / 讨论与任务](https://github.com/EricBeckham/NexusOne/issues)

有关已合入范围、剩余工作、测试 `PASS/SKIP` 与实际环境限制，请以上述持续更新的文档和对应代码为准。

## 如何参与

欢迎通过 [GitHub Issues](https://github.com/EricBeckham/NexusOne/issues) 提交可复现的缺陷、改进建议与文档问题。计划贡献代码时，建议先就目标范围创建 Issue，再提交包含变更动机、测试步骤和验证结果的 Pull Request。涉及隔离、鉴权、数据安全的更改应特别说明潜在风险及回归测试。

## 开源许可

本项目计划采用 [MIT License](./LICENSE)（**仅在维护者确认版权及第三方代码授权并合并 LICENSE 文件后生效**）。依赖项和引入的第三方资源仍受其各自许可证约束。
