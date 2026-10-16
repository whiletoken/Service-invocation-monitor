---
name: precision-code-analysis
description: >-
  用于指导在大型或复杂代码库中进行高精准、零浪费的代码阅读与架构分析。当需要调查代码逻辑、排查 Bug、定位核心流程或规划重构时激活此技能。强制执行三大核心原则：代码读取能力优化减少无效代码读取、优先读取已有技术沉淀与架构文档、由点到面（Point-to-Surface）渐进式分析代码。
---

# 高精准代码阅读与由点到面渐进式分析指南 (Precision Code Analysis)

本技能确立了高效、精准、低开销的代码分析与阅读标准范式，旨在彻底杜绝大水漫灌式全量文件扫读，保护上下文窗口，提升推理与问题排查的信噪比。

---

## 核心原则一：代码读取能力优化，减少无效代码读取 (Zero-Waste Reading)

大水漫灌式读取整个代码库或无节制地通篇展示大文件，会导致模型上下文严重污染、信噪比骤降、推演注意力涣散。必须严格执行“按需定量”读取：

1. **精准切片规范（Line-Level Slicing）**：
   - 严禁一次性读取动辄几百行至上千行的代码文件。
   - 使用文件读取工具时，**必须指定 `StartLine` 与 `EndLine`**，将单次读取范围严格控制在与目标逻辑强相关的 **50 ~ 100 行** 内。
   - 示例：若仅需了解 `MonitorOutputToolWindow.createToolWindow` 的实现，只读取该方法所在的行区间（如第 50 到 80 行），绝不读取其余无关行。

2. **噪音资产主动屏蔽（Noise Shielding）**：
   - 严禁遍历或读取自动生成的构建目录与缓存：如 `build/`、`.gradle/`、`dist/`、`.idea/`、`target/` 等。
   - 严禁遍历所有深层第三方库或中间件的内部细节。
   - 遇到未压缩的映射文件、大型日志文件（如 `.log`）或二进制文件时，使用专门的针对性工具提取摘要，禁止全文加载。

3. **目标达成即刻终止（Early Stopping）**：
   - 当已获取当前分析步骤所需的关键判断依据（如类继承关系、方法签名、ThreadLocal 变量定义或接口契约）后，**立即停止**继续读取该文件的其他方法或下游旁支，杜绝无意义的代码漫游。

---

## 核心原则二：优先读取已有的有效信息来分析代码 (High-Value Information First)

任何成熟的项目通常已经积累了高价值的架构文档与技术资产。通过既有信息切入，能够在毫秒级内获取全局心智模型，效率数倍于从零逆向源码：

```
高价值信息优先级金字塔：
┌───────────────────────────────────────────────┐
│ 1. 架构报告 / 技术分析文档 (README / 分析报告)   │ ← 宏观全貌、调用时序、设计亮点
├───────────────────────────────────────────────┤
│ 2. 构建描述与清单配置 (build.gradle / plugin.xml) │ ← 模块边界、依赖体系、扩展点
├───────────────────────────────────────────────┤
│ 3. 提交历史与演进记录 (CHANGELOG / git log)    │ ← 演进动机、历史缺陷、设计意图
├───────────────────────────────────────────────┤
│ 4. 核心接口与抽象定义 (Interface / Abstract)     │ ← 业务契约、交互协议
├───────────────────────────────────────────────┤
│ 5. 具体实现源码 (Implementation Details)      │ ← 仅在上述信息不足时切入局部
└───────────────────────────────────────────────┘
```

1. **技术报告与架构设计优先**：
   - 在本工程中，优先查阅如 [项目技术分析报告.md](file:///c:/Users/liuju/Documents/GitHub/Service-invocation-monitor/%E9%A1%B9%E7%9B%AE%E6%8A%80%E6%9C%AF%E5%88%86%E6%9E%90%E6%8A%A5%E5%91%8A.md)、[README.md](file:///c:/Users/liuju/Documents/GitHub/Service-invocation-monitor/README.md)。
   - 借助文档中整理好的 Mermaid 序列图（如 Span 采集出栈时序）、模块依赖拓扑与核心表单，快速理解生命周期与数据流动。

2. **清单与配置优先**：
   - 遇到平台扩展、组件加载问题，先读 [plugin.xml](file:///c:/Users/liuju/Documents/GitHub/Service-invocation-monitor/monitor-plugin/src/main/resources/META-INF/plugin.xml) 中的 `<extensions>`、`<actions>` 或 [build.gradle](file:///c:/Users/liuju/Documents/GitHub/Service-invocation-monitor/build.gradle) 中的依赖声明，而非猜测各个 Class 的注入方式。

3. **版本日志与 Git 提交优先**：
   - 优先查阅 [CHANGELOG.md](file:///c:/Users/liuju/Documents/GitHub/Service-invocation-monitor/CHANGELOG.md) 查看最近版本解决的缺陷（如 O(N^2) 复杂度优化、内存泄露修复）。
   - 善用 `git log -S <symbol> --oneline` 或 `git log -L <start>,<end>:<file>` 探查目标代码块的最后一次修改原因。

---

## 核心原则三：由点到面渐进式分析，严禁全部读取 (Point-to-Surface Traversal)

采用三级渐进式分析法（Point -> Line -> Surface），以最小代价锁定核心：

```
                    【点 Point】
                高精度符号/注解/报错定位
                        │
                        ▼
                    【线 Line】
                直接调用上下游极简链路
                        │
                        ▼
                   【面 Surface】
              仅按需拓展至类边界/模块接口
```

### 1. 第一级：锚点定位（Point）
- **不要**用目录递归罗列出所有文件逐个猜测。
- **必须**使用精确检索命令，基于异常类、特定注解、日志文本或关键字段锁定唯一锚点：
  ```bash
  # 精准匹配符号引用
  git grep -n "ContextManager.createSpan"
  git grep -n "@RestController"
  ```
- 锁定具体的行号后，仅切片读取该行上下 20~30 行代码。

### 2. 第二级：调用切片（Line）
- 从定位到的锚点出发，只单向追踪**直接调用方（Caller）**或**核心被调用方（Callee）**。
- 沿着单一主干调用切片排查，严禁在每一个分支点上横向遍历展开次要类。
- 示例：排查 TraceSegment 构造 -> 只看 `ContextManager.stopSpan()` -> `RuntimeContext.getTraceSegment()` -> `fillingEachSegment()`，不看与此无关的其他插件类。

### 3. 第三级：边界上下文扩充（Surface）
- **仅当**发现需要确认类的生命周期绑定（如 `Disposable` / `ThreadLocal` 清理）、多线程可见性（`volatile` / 并发锁）或父类继承契约时，才从当前方法扩展到包含该方法的类的头部字段与类定义。
- 形成能够支撑当前修改决定的**最小闭环上下文**，立即开始编写解决方案或测试，坚决不向外扩大阅读范围。

---

## 行为对比矩阵 (Anti-Patterns vs. Best Practices)

| 场景 | ❌ 反模式（大水漫灌） | ✅ 最佳实践（由点到面精准分析） |
| :--- | :--- | :--- |
| **项目理解** | 一开始就 `Get-ChildItem -Recurse` 遍历全部源码文件，逐个全文阅读 | 优先查看 `项目技术分析报告.md`、`README.md` 与核心类图，快速建立心智模型 |
| **问题排查** | 将可疑的 4~5 个完整 Java 文件全部从第 1 行读到第 300 行 | 用 `git grep` 抓取异常或关键调用点，只读取上下 40 行切片代码 |
| **寻找过滤规则** | 打开整个项目所有拦截器类进行肉眼扫描 | 优先查阅已有常量类 `ClassFilterConstants.java` 与已记录的 `CHANGELOG.md` |
| **验证修改点** | 盲目通读上层调用者的所有实现细节 | 追踪单条调用链至核心接入点，验证切面或参数契约后即止 |
