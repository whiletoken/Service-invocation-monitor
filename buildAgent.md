# 插件打包指南 (Plugin Packaging Guide)

本项目已实现自动化一键打包，彻底解决了以往繁琐的手工多步骤打包问题（无需再按旧流程手动执行 `packCore -> monitor-plugin.build -> packPlugin -> packAgent -> monitor-plugin.build`）。

---

## 一键打包方式 (推荐)

### 1. Windows 环境

直接在项目根目录运行或双击：
```cmd
build-plugin.bat
```
或者在 PowerShell 中运行：
```powershell
.\build-plugin.ps1
```
> **提示**：脚本会自动检测并配置 JDK 环境（自动优先使用兼容 Gradle 9 的 JDK 17+ 与 IntelliJ 平台配套 JBR 25 工具链），无需手动切换 `JAVA_HOME`。

可选参数：
- `-Clean`：打包前清理历史缓存和构建产物，例如 `.\build-plugin.ps1 -Clean`

---

### 2. Linux / macOS / CI 环境

在终端中执行：
```bash
chmod +x build-plugin.sh
./build-plugin.sh
```

---

### 3. Gradle 原生命令打包

如果习惯直接使用 Gradle 命令行，可以直接执行：
```bash
./gradlew :monitor-plugin:packagePlugin -x test
```

---

## 构建与打包产物

打包成功后，最终的插件安装包将自动收集并输出至项目根目录的 **`dist/`** 目录：

```
dist/
└── monitor-plugin-<版本号>.zip
```

### 插件包内部结构说明
生成的 `.zip` 包符合 IntelliJ Platform 标准插件规范，并已自动完成全量内嵌：
- `monitor-plugin/lib/monitor-plugin-<version>.jar`：IDEA 插件核心 UI 与交互逻辑
- `monitor-plugin/lib/monitor-agent-<version>-all.jar`：JavaAgent 探针 Fat Jar（包含 ByteBuddy 与 Agent 引导逻辑）
- `monitor-plugin/lib/plugins/*.jar`：各字节码增强子插件（Dubbo、Spring MVC、Spring 5、JDK Thread 增强）
- `monitor-plugin/lib/*.jar`：插件所依赖的第三方运行时库（Hutool、Guava 等）

---

## 插件安装步骤

1. 打开 **IntelliJ IDEA**。
2. 进入设置：
   - Windows/Linux: `File -> Settings -> Plugins`
   - macOS: `IntelliJ IDEA -> Settings -> Plugins`
3. 点击顶部的设置齿轮图标 ⚙️，选择 **`Install Plugin from Disk...`**。
4. 在弹出的文件选择器中，定位并选中 `dist/monitor-plugin-<版本号>.zip`。
5. 点击 **OK**，随后根据提示点击 **Restart IDE** 重启 IntelliJ IDEA 即可生效。