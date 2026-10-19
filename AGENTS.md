# 工作区核心强制规范 (Workspace Core Rules)

本项目开发、代码审查、排查诊断与重构必须严格执行以下规则。此规范对所有会话无条件始终生效（Always-On）。

---

## 1. 代码读取与精准切片规范 (Zero-Waste & Line-Level Slicing)

- **强制行级切片（Mandatory Line Slicing）**：
  - 调用 `view_file` 查看任何代码或配置时，**必须指定 `StartLine` 与 `EndLine`**。
  - 单次读取范围严格控制在与目标强相关的 **50 ~ 100 行** 关键切片内。
  - **严禁**无行号范围直接全量通读大文件（超过 100 行）。
- **屏蔽无关与生成资产（Noise Shielding）**：
  - 严禁在 `build/`、`.gradle/`、`dist/`、`.idea/`、`.vscode/` 等临时与构建产物目录中进行全文检索或源码读取。
- **目标达成即停（Early Stopping）**：
  - 一旦获取到支撑当前步骤所需的接口契约、方法逻辑、配置块或字段定义，立即停止进一步读取，杜绝无意义的代码漫游。

---

## 2. 优先利用已有高价值沉淀 (High-Value Information First)

在着手调查、设计或改动代码前，必须严格按照优先级获取信息：
1. **架构与技术文档优先**：优先查阅 [项目技术分析报告.md](file:///c:/Users/liuju/Documents/GitHub/Service-invocation-monitor/%E9%A1%B9%E7%9B%AE%E6%8A%80%E6%9C%AF%E5%88%86%E6%9E%90%E6%8A%A5%E5%91%8A.md)、[README.md](file:///c:/Users/liuju/Documents/GitHub/Service-invocation-monitor/README.md)、[buildAgent.md](file:///c:/Users/liuju/Documents/GitHub/Service-invocation-monitor/buildAgent.md)。
2. **构建与扩展清单优先**：优先查阅 [build.gradle](file:///c:/Users/liuju/Documents/GitHub/Service-invocation-monitor/build.gradle)、[settings.gradle](file:///c:/Users/liuju/Documents/GitHub/Service-invocation-monitor/settings.gradle) 及 `plugin.xml` 明确模块边界。
3. **变更日志与按需溯源**：优先查阅 [CHANGELOG.md](file:///c:/Users/liuju/Documents/GitHub/Service-invocation-monitor/CHANGELOG.md) 明确演进历程；**仅在代码设计意图或历史缺陷动机不明时**针对特定符号回溯 Git，严禁在常规开发或分析中机械、反复读取 `git log` / `git show`。
4. **具体源码切片**：仅在上述信息无法覆盖时，针对性切入核心代码。

---

## 3. 由点到面渐进式分析 (Point-to-Surface Traversal)

- **锚点定位 (Point)**：通过高精准针对性搜索（如 `grep_search` 关键类名、方法名、配置项或符号）快速定位目标行号，绝不从根目录顺次通读。
- **调用切片 (Line)**：从锚点出发，单向追踪直接调用者（Caller）与被调用者（Callee），不展开无直接关联的旁支。
- **最小边界 (Surface)**：仅在需要明确类生命周期、并发安全或容器注入时，适度扩展至最小闭环上下文。

---

## 4. 现代平台与构建演化规范 (Modern Platform Compliance)

- 遵循 IntelliJ Platform 2026+ 与 Gradle 9.x 兼容要求：严禁只改版本号，必须做到线程模型合规（BGT/EDT 分离）、强封装（`--add-opens`）、内存防泄露（`Disposer` 绑定）、构建产物规范收拢。

---

## 5. 代码提交规范 (Git Commit Standards)

- 提交代码时必须遵循 Conventional Commits 规范，严格对照 [git-commit-workflow](file:///c:/Users/liuju/Documents/GitHub/Service-invocation-monitor/.agents/skills/git-commit-workflow/SKILL.md) 技能执行：
  - 检查工作区纯净度，严禁将 `.idea/`、临时产物或敏感信息混入；
  - 遵循 `<type>(<scope>): <subject>` 结构，中大型改动提供详尽 Body 说明改动背景与收益；
  - 保持提交原子性（Atomic Commit），单一提交聚焦单一职责。

