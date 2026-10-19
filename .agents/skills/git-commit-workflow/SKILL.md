---
name: git-commit-workflow
description: >-
  用于指导自动化、规范化的 Git 代码提交工作流。严格遵循 Conventional Commits 规范与专业程序员开发标准，涵盖工作区纯净度检查、敏感信息过滤、原子化暂存、结构化 Commit Message 自动生成与提交自检。当用户要求提交代码、规范提交记录或一键归档变更时激活此技能。
---

# 专业级 Git 代码自动化提交规范指南 (Git Commit Workflow)

本指南确立了在现代工程开发中执行 Git 代码提交的标准流程与准则，确保每一次提交历史清晰、可追溯、原子化，严格遵循业界标准的 **Conventional Commits 1.0.0** 规范。

---

## 核心原则

1. **原子化提交（Atomic Commit）**：
   - 每次提交聚焦于单一目的（一个功能点、一个 Bug 修复或一次特定重构）。
   - 严禁将相互独立、风马牛不相及的多个变动打包成一个庞杂晦涩的 Commit。
2. **工作区纯净（Clean Workspace）**：
   - 严禁将编译中间件、临时构建产物（`build/`）、IDE 本地私有配置（`.idea/`、`.vscode/`）或敏感凭据提交至版本库。
3. **动机先于实现（Why Over What）**：
   - Commit Message 的核心价值是记录“为什么做这个变更（Motivation/Why）”和“带来了什么业务/技术影响”，而不仅仅是直白地复述代码改了哪几行。

---

## 标准提交工作流 (4 步法)

```
┌────────────────────────┐
│ 1. 提交前自检与过滤    │  检查未跟踪文件、临时调试代码、屏蔽敏感资产
└───────────┬────────────┘
            │
            ▼
┌────────────────────────┐
│ 2. 变更分类与针对性暂存│  避免盲目 git add .，按模块/主题针对性暂存
└───────────┬────────────┘
            │
            ▼
┌────────────────────────┐
│ 3. 结构化 Commit 生成  │  遵循 Conventional Commits 规范（Type/Scope/Body）
└───────────┬────────────┘
            │
            ▼
┌────────────────────────┐
│ 4. 提交执行与结果核验  │  安全执行提交并调用 git log -1 --stat 校验成果
└────────────────────────┘
```

---

## 阶段一：提交前工作区自检 (Pre-Commit Health Check)

在执行任何暂存操作前，必须进行工作区健康检查：

1. **状态透视**：
   - 执行 `git status --short` 确认所有变动文件的清单。
2. **防噪音排查**：
   - 检查是否有新增的未忽略文件（如 `*.log`、`*.class`、临时测试输出）；
   - 确认 `.gitignore` 是否已覆盖构建中间产物（如 `build/`、`.gradle/`、`dist/*.zip` 除非专用于版本发版）。
3. **残留调试代码清理**：
   - 确认源码中无临时插入的 `System.out.println`、调试用断点标志或无意义注释。
4. **编译基线保护**：
   - 若涉及代码与构建脚本改动，必须确保当前工程处于可成功编译/打包状态，严禁提交无法编译的残缺代码（Broken Build）。

---

## 阶段二：变更分类与精准暂存 (Atomic Staging)

1. **针对性暂存（Targeted Staging）**：
   - 优先通过明确的文件路径或模块模式执行 `git add <files>`，例如：
     ```bash
     git add build.gradle settings.gradle
     git add monitor-agent/
     ```
   - 仅在工作区中所有变动均紧密属于同一任务且已严格确认无误时，才执行全量暂存。
2. **审查暂存区（Diff Inspection）**：
   - 执行 `git diff --cached --stat` 确认暂存的文件与预期完全一致，无误入的资产。

---

## 阶段三：规范化 Commit Message 结构与生成标准

Commit Message 必须严格遵循 **Conventional Commits** 格式：

```
<type>(<scope>): <subject>

[可选 body: 详细说明改动背景、设计考量与核心方案]

[可选 footer: 关联 Issue 或 Breaking Changes 说明]
```

### 1. 提交类型规范（Type）

| Type | 适用场景 | 示例 |
| :--- | :--- | :--- |
| **`feat`** | 新增业务功能、模块或扩展点 | `feat(search): 支持调用链控制台关键字实时高亮搜索` |
| **`fix`** | 修复已知的缺陷、运行时异常或兼容性问题 | `fix(agent): 修复多线程拦截器中 ThreadLocal 潜在的内存泄露` |
| **`refactor`**| 既未新增功能也未修复 Bug 的代码结构优化与重构 | `refactor(build): 统一全模块构建输出至根目录并按层级分类` |
| **`perf`** | 提升运行性能、减少内存占用或优化算法复杂度 | `perf(trace): 将 Span 栈查找从 O(N^2) 优化为 O(1) 索引` |
| **`build`** | 构建系统、Gradle/Maven 插件、外部依赖版本变更 | `build(gradle): 适配 Gradle 9 与 IntelliJ Platform 2.x` |
| **`chore`** | 日常辅助事务、工作区规范、Git 规则或脚本维护 | `chore(rules): 增加根目录 AGENTS.md 规范与自动化工作流` |
| **`docs`** | 仅涉及文档变动（README、技术分析报告、CHANGELOG） | `docs: 更新项目技术分析报告中集中式构建产物拓扑说明` |
| **`test`** | 增加或修复单元测试、集成测试用例 | `test(agent): 补充跨线程上下文传递集成测试` |
| **`style`** | 不影响代码运行逻辑的格式、分号、缩进调整 | `style: 规范代码格式并清理多余换行` |

### 2. 影响范围规范（Scope）
推荐结合本工程的模块架构进行标记：
- `build`：构建体系、打包脚本
- `agent`：JavaAgent 探针引导与入口
- `core`：探针核心基础设施（上下文、拦截器、加载器）
- `plugin`：IDEA 插件展示层（ToolWindow、Console）
- `dubbo` / `spring` / `thread`：各字节码插桩子插件
- `rules`：工作区规范与技能库

### 3. 描述规范（Subject）
- **语言**：推荐中文精炼表达（或英文祈使句）；
- **字数**：控制在 50 个字符以内；
- **标点**：末尾严禁加句号或分号；
- **核心句式**：动词 + 宾语（如 `统一构建输出至根目录 build/ 并按层级分类`）。

### 4. 正文说明规范（Body）
对于中大型重构或多模块变动，**强烈推荐编写详细 Body**，采用分项条目陈述：
- **背景与动机（Why）**：为什么需要本次改动？
- **核心措施（What/How）**：改动了哪些关键设计与配置？
- **影响范围与收益**：带来了什么架构收益或行为变更。

---

## 阶段四：安全提交与结果校验

### 1. Windows / PowerShell 环境安全提交
在 Windows PowerShell 环境下，为了避免多行文本、引号或特殊字符被错误转义，推荐使用标准的 `-m` 多行参数或利用暂存信息提交：
```powershell
git commit -m "refactor(build): 统一全模块构建输出至根目录 build 并按层级存放" `
           -m "- 构建收拢：在 build.gradle 中通过 layout.buildDirectory 将 8 个子模块 build 文件夹统一收纳至根目录" `
           -m "- 分类存放：按 agent/、plugins/、monitor-plugin/、root/ 四大架构层级归类" `
           -m "- 任务重构：现代化改造 packAgent 与 packPlugin 任务，移除路径硬编码" `
           -m "- 规范固化：建立根目录 AGENTS.md 强制切片与零漫游规范，完善技术分析报告"
```

### 2. 提交结果核验
提交后执行校验命令，确认提交已落盘且关联文件完整：
```bash
git log -1 --stat
```

---

## 检查清单（Pre-Commit Checklist）

- [ ] 是否确认未暂存 `.idea/`、临时构建产物或敏感配置文件？
- [ ] 当前代码与编译脚本是否已通过本地构建验证（`BUILD SUCCESSFUL`）？
- [ ] Commit Message 是否符合 `<type>(<scope>): <subject>` 规范？
- [ ] 涉及重要功能或架构调整时，是否已同步维护 [CHANGELOG.md](file:///c:/Users/liuju/Documents/GitHub/Service-invocation-monitor/CHANGELOG.md)？
- [ ] 提交是否保持原子性，未将无关任务混入同一 Commit？
