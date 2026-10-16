---
name: idea-plugin-modernization
description: >-
  用于指导 IntelliJ IDEA 插件向现代高版本平台（2024.x/2025.x/2026.x 及以上）进行全面升级、代码级兼容性重构、线程模型合规、内存泄露排查与性能安全加固。当用户要求升级 IDEA 插件、适配最新 IDEA、解决插件在新版本崩溃/报错/卡死、或避免仅改版本号的浅层修改时，必须激活此技能。
---

# IntelliJ IDEA 插件现代平台升级与深度兼容性重构指南

本技能总结了从旧版 IntelliJ IDEA 插件（如 2020~2023）向现代乃至最新版本（2025.x/2026.x+）升级过程中的核心经验教训与标准化重构工作流。

> **核心戒律：严禁只改版本号！**
> 现代 IntelliJ Platform 经历了底层的模块化隔离、线程模型革命、API 废弃与分发模型统一。浅层修改版本号几乎必然导致运行时报 `NoSuchMethodError`、`InaccessibleObjectException`、UI Freeze 假死卡顿或 Project 内存泄露。必须执行“代码级深度审查与重构”。

---

## 阶段一：本地环境与平台基准感知（Ground Truth Discovery）

在修改任何配置前，不要基于历史假设推断版本，必须探查实际的宿主环境：
1. **探查宿主 IDE 与 JBR 版本**：
   - 查看本地 IDEA 安装路径（如 `build.txt`、`product-info.json`）获取真实内部构建号（如 `262.xxxx` 表示 2026.2 版本）。
   - 检查捆绑的 JBR 版本（`jbr/bin/java.exe -version`），现代平台通常运行在 JDK 17、21 乃至 JBR 25+ 上。
2. **确定平台基线分支（Branch Build Number）**：
   - 2024.x 系列对应 `241` ~ `243`；
   - 2025.x 系列对应 `251` ~ `253`；
   - 2026.x 系列对应 `261` ~ `263`。

---

## 阶段二：现代构建系统迁移（IntelliJ Platform Gradle Plugin 2.x）

旧版 `org.jetbrains.intellij:1.x` 已经彻底废弃，必须升级为官方最新的 2.x 插件。

### 1. `build.gradle` 声明标准
```groovy
plugins {
    id 'java'
    id 'org.jetbrains.intellij.platform' version '2.19.0' // 使用 2.x 现代构建插件
}

java {
    sourceCompatibility = JavaVersion.VERSION_21 // 2026 平台强制要求 Java 21+ 字节码
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories() // 平台官方软件源
    }
}

dependencies {
    intellijPlatform {
        // 2025.3+ 以后 JetBrains 统一了平台分发包，使用 intellijIdea("版本号") 代替旧版 intellijIdeaCommunity
        intellijIdea("2026.2.3")
        bundledPlugin("com.intellij.java") // 所需内置插件
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
            untilBuild = provider { null } // open-ended，允许高版本平滑兼容
        }
    }
}
```

---

## 阶段三：插件清单 `plugin.xml` 严格规范审查

1. **标签去重与格式校验**：
   - `<vendor>` 标签必须严格唯一，禁止出现两个 `<vendor>`。推荐形式：`<vendor url="..." email="...">名称</vendor>`。
2. **模块与插件依赖显式化**：
   - 现代平台实行强模块化加载，必须包含：
     ```xml
     <depends>com.intellij.modules.platform</depends>
     <depends>com.intellij.modules.java</depends> <!-- 若涉及 Java PSI 或执行配置 -->
     ```
3. **通知组必须静态声明**：
   - 自 2020.3 起废弃、2024+ 移除直接 `new NotificationGroup(...)` 构造，未注册将导致通知丢失：
     ```xml
     <extensions defaultExtensionNs="com.intellij">
         <notificationGroup id="Your Plugin Group" displayType="BALLOON"/>
     </extensions>
     ```

---

## 阶段四：线程模型重构（Threading Model Compliance）

现代 IntelliJ Platform 对 EDT（UI 线程）执行时长有严苛的检测（`FreezeWatcher` / `PerformanceWatcher`），任何阻塞行为都会导致卡顿告警。

### 1. Action 更新后台化（`ActionUpdateThread.BGT`）
所有自定义 `AnAction` 必须明确指定在后台线程执行 `update`，严禁使用已弃用的旧版 EDT 模式：
```java
public class MyAction extends AnAction implements DumbAware {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT; // 强制要求在后台线程评估可用性
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        e.getPresentation().setEnabledAndVisible(project != null && !project.isDisposed());
    }
}
```

### 2. 磁盘 I/O 与 UI 渲染异步分离
涉及文件读写、日志轮询或外部进程通信的操作，必须剥离出 EDT：
```java
// 1. 在后台线程池中执行磁盘 I/O 或高耗时操作
ApplicationManager.getApplication().executeOnPooledThread(() -> {
    if (project.isDisposed()) return;
    
    // 执行文件增量读取 / 数据解析
    List<DataDTO> results = readFromDisk();

    // 2. 解析完毕后安全调度至 EDT 进行 UI 渲染
    if (!results.isEmpty()) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;
            for (DataDTO dto : results) {
                consoleView.print(dto.getText(), contentType);
            }
        }, ModalityState.defaultModalityState());
    }
});
```

### 3. 静态复用渲染样式对象
禁止在循环中每次调用 `TextAttributesKey.createTextAttributesKey(...)`，必须在静态块中一次性初始化静态单例 `ConsoleViewContentType`，避免大量垃圾对象引起频繁 GC。

---

## 阶段五：内存泄露防御与生命周期管理（Memory & Disposer）

在 IntelliJ IDEA 插件开发中，**持有已关闭项目的强引用是第一大内存泄露源**。

### 1. ToolWindow 与 ConsoleView 的 Disposer 绑定
```java
// 使用 Disposer 绑定当前 Project 的生命周期，项目关闭时自动注销并释放资源
Disposer.register(project, () -> {
    consoleViews.remove(project);
    Disposer.dispose(consoleView);
});

// 使用现代工厂 API
ContentFactory contentFactory = ContentFactory.getInstance(); // 废弃 SERVICE.getInstance()
Content content = contentFactory.createContent(component, title, false);
toolWindow.getContentManager().addContent(content); // 严禁重复 addContent()
```

### 2. `Configurable` 组件懒加载与释放
严禁在 `Configurable` 构造函数中实例化 Swing 组件：
```java
public class MyConfigurable implements Configurable {
    private JPanel rootPanel;
    private JTextField inputField;

    @Override
    public @Nullable JComponent createComponent() {
        if (rootPanel == null) {
            initUI(); // 懒加载：仅在打开设置页面时创建
        }
        return rootPanel;
    }

    @Override
    public void reset() {
        if (rootPanel != null) {
            // 恢复配置值
            inputField.setText(MySettings.getInstance().getValue());
        }
    }

    @Override
    public void disposeUIResources() {
        // 关闭弹窗时释放 Swing 控件强引用，防止内存泄漏
        rootPanel = null;
        inputField = null;
    }
}
```

### 3. 数据持久化服务现代化
为持久化状态组件增加现代服务注解：
```java
@Service(Service.Level.APP)
@State(name = "MySettings", storages = {@Storage("my-settings.xml")})
public class MySettings implements PersistentStateComponent<MySettings> {
    public static MySettings getInstance() {
        return ApplicationManager.getApplication().getService(MySettings.class);
    }
    ...
}
```

---

## 阶段六：彻底清除内部私有 API（Forbidden Internal APIs）

### 严禁使用 `impl` 内部包
在 IntelliJ 平台中，任何位于 `com.intellij.*.impl.*` 下的类都是非公开内部实现（Internal API），平台验证器会判定为严重合规缺陷，且随时可能被重命名或移除。
* **错误**：`((PsiClassImpl) psiElement).getContext()` 或 `(PsiJavaFileImpl) file`
* **正确**：使用公共 OpenAPI 接口：
  ```java
  if (element instanceof PsiClass) {
      PsiFile containingFile = ((PsiClass) element).getContainingFile();
      if (containingFile instanceof PsiJavaFile) {
          String pkg = ((PsiJavaFile) containingFile).getPackageName();
      }
  }
  ```

---

## 阶段七：高版本 Java (JDK 17/21/25) 强封装与运行时适配

现代 IDE 运行在强封装的 JDK 之上，涉及到 Java Agent、ByteBuddy 增强或底层反射时：
1. **注入强封装解开参数**：
   在 `JavaProgramPatcher` 中确保为启动参数注入：
   ```java
   vmParametersList.addParametersString("--add-opens=java.base/java.lang=ALL-UNNAMED");
   ```
2. **跨版本 API 类型变迁的反射容错**：
   例如 `IdeaPluginDescriptor.getPath()` 在旧版返回 `File`，新版返回 `java.nio.file.Path`，直接强转或调用会在不同版本间报 `NoSuchMethodError`。必须提供双向兼容解析：
   ```java
   private static File getPluginDirectory(IdeaPluginDescriptor descriptor) {
       if (descriptor == null) return null;
       try {
           Method m = descriptor.getClass().getMethod("getPluginPath");
           Object res = m.invoke(descriptor);
           if (res instanceof java.nio.file.Path) return ((java.nio.file.Path) res).toFile();
       } catch (Throwable ignored) {}
       try {
           Method m = descriptor.getClass().getMethod("getPath");
           Object res = m.invoke(descriptor);
           if (res instanceof File) return (File) res;
           if (res instanceof java.nio.file.Path) return ((java.nio.file.Path) res).toFile();
       } catch (Throwable ignored) {}
       return null;
   }
   ```
3. **测试运行器安全提取测试类名**：
   在测试（如 JUnit / SpringBootTest）运行时，启动主类常为 `com.intellij.rt.junit.JUnitStarter`。不要直接根据启动类截取包名，通过反射解析配置数据提取目标测试类：
   ```java
   Method method = runConfiguration.getClass().getMethod("getPersistentData");
   Object data = method.invoke(runConfiguration);
   String testClass = (String) data.getClass().getMethod("getMainClassName").invoke(data);
   ```

---

## 检查清单（Pre-Release Checklist）

每次完成升级后，必须逐条对照：
- [ ] 构建脚本采用 IntelliJ Platform Gradle Plugin 2.x，配置了有效的 `sinceBuild`。
- [ ] `plugin.xml` 中 `<vendor>` 唯一，模块依赖（`<depends>`）声明完整。
- [ ] 所有 `AnAction` 实现了 `getActionUpdateThread()` 并返回 `ActionUpdateThread.BGT`。
- [ ] 所有高耗时或磁盘 I/O 操作均移出了 EDT 主线程。
- [ ] 代码中**零引用** `com.intellij.*.impl.*` 私有类。
- [ ] `Configurable` 实现了 `createComponent()` 懒加载、`reset()` 与 `disposeUIResources()` 内存释放。
- [ ] `ToolWindow` 中的资源正确注册到 `Disposer.register(project, ...)`。
- [ ] NotificationGroup 已经在 `plugin.xml` 中静态声明。
- [ ] 涉及 Agent 拦截器时，所有业务方法包含 `try-finally` 闭环出栈逻辑，杜绝 ThreadLocal 泄露。
- [ ] 针对高版本 JDK 运行时注入了 `--add-opens` 参数。
- [ ] 变动条目已完整同步至 `CHANGELOG.md`。
