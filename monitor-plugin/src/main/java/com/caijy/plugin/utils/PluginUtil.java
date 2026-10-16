package com.caijy.plugin.utils;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.caijy.agent.core.console.TraceConsoleDTO;
import com.caijy.agent.core.constants.AgentConstant;
import com.caijy.agent.core.utils.FileCache;
import com.caijy.plugin.constants.PluginAgentConstant;
import com.caijy.plugin.ui.MonitorOutputToolWindow;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;

public class PluginUtil {

    private static final ConsoleViewContentType DARK_GRAY_OUTPUT;
    private static final ConsoleViewContentType CYAN_OUTPUT;
    private static final ConsoleViewContentType GREEN_OUTPUT;
    private static final ConsoleViewContentType RED_OUTPUT;

    static {
        TextAttributes normalAttr = ConsoleViewContentType.NORMAL_OUTPUT.getAttributes();
        DARK_GRAY_OUTPUT = new ConsoleViewContentType("MONITOR_DARK_GRAY",
            new TextAttributes(JBColor.DARK_GRAY, normalAttr.getBackgroundColor(),
                normalAttr.getEffectColor(), normalAttr.getEffectType(), normalAttr.getFontType()));
        CYAN_OUTPUT = new ConsoleViewContentType("MONITOR_CYAN",
            new TextAttributes(JBColor.CYAN, normalAttr.getBackgroundColor(),
                normalAttr.getEffectColor(), normalAttr.getEffectType(), normalAttr.getFontType()));
        GREEN_OUTPUT = new ConsoleViewContentType("MONITOR_GREEN",
            new TextAttributes(JBColor.GREEN, normalAttr.getBackgroundColor(),
                normalAttr.getEffectColor(), normalAttr.getEffectType(), normalAttr.getFontType()));
        RED_OUTPUT = new ConsoleViewContentType("MONITOR_RED",
            new TextAttributes(JBColor.RED, normalAttr.getBackgroundColor(),
                normalAttr.getEffectColor(), normalAttr.getEffectType(), normalAttr.getFontType()));
    }

    /**
     * 记录各项目已读取的日志字节偏移量，实现极低开销的增量读取
     */
    private static final Map<String, Long> lastFileOffsetCache = new ConcurrentHashMap<>();

    /**
     * 缓存已解析出的 agent jar 路径，避免重复深度遍历目录
     */
    private static volatile String cachedAgentJarPath = null;
    private static final Object JAR_PATH_LOCK = new Object();

    /**
     * 获取核心Jar路径
     *
     * @return String
     */
    public static String getAgentCoreJarPath() {
        return getJarPath(PluginAgentConstant.AGENT_PREFIX, PluginAgentConstant.AGENT_SUFFIX);
    }

    public static String getAgentCoreJarPath2() {
        return getJarPath2(PluginAgentConstant.AGENT_PREFIX, PluginAgentConstant.AGENT_SUFFIX);
    }

    /**
     * 不处理含空格
     *
     * @param startWith:
     * @param suffix:
     * @return
     * @author liguang
     * @date 2023/1/6 11:00 上午
     **/
    private static String getJarPath2(String startWith, String suffix) {
        if (cachedAgentJarPath != null && new File(cachedAgentJarPath).exists()) {
            return cachedAgentJarPath;
        }
        synchronized (JAR_PATH_LOCK) {
            if (cachedAgentJarPath != null && new File(cachedAgentJarPath).exists()) {
                return cachedAgentJarPath;
            }
            PluginId pluginId = PluginId.getId(PluginAgentConstant.PLUGIN_ID);
            IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(pluginId);
            if (descriptor == null) {
                MessageUtil.warn("Service Invocation Monitor: plugin descriptor not found!");
                return StrUtil.EMPTY;
            }
            File pluginDir = getPluginDirectory(descriptor);
            if (pluginDir == null || !pluginDir.exists()) {
                MessageUtil.warn("Service Invocation Monitor: plugin directory not found!");
                return StrUtil.EMPTY;
            }
            List<File> files = FileUtil.loopFiles(pluginDir);
            for (File file : files) {
                String name = file.getName();
                if (name.startsWith(startWith) && name.endsWith(suffix)) {
                    cachedAgentJarPath = FileUtil.getCanonicalPath(file);
                    return cachedAgentJarPath;
                }
            }
        }
        return StrUtil.EMPTY;
    }

    /**
     * 兼容各版本 IntelliJ Platform 获取插件安装根目录
     * 现代版本（2024.2+ / 2026.x）返回 java.nio.file.Path，早期版本返回 java.io.File
     */
    private static File getPluginDirectory(IdeaPluginDescriptor descriptor) {
        if (descriptor == null) {
            return null;
        }
        try {
            java.lang.reflect.Method m = descriptor.getClass().getMethod("getPluginPath");
            Object res = m.invoke(descriptor);
            if (res instanceof java.nio.file.Path) {
                return ((java.nio.file.Path) res).toFile();
            }
        } catch (Throwable ignored) {
        }
        try {
            java.lang.reflect.Method m = descriptor.getClass().getMethod("getPath");
            Object res = m.invoke(descriptor);
            if (res instanceof File) {
                return (File) res;
            } else if (res instanceof java.nio.file.Path) {
                return ((java.nio.file.Path) res).toFile();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * 根据jar包的前缀名称获路径
     *
     * @param startWith 前缀名称
     * @return String
     */
    private static String getJarPath(String startWith, String suffix) {
        String pathStr = getJarPath2(startWith, suffix);
        if (StrUtil.isNotEmpty(pathStr) && StrUtil.contains(pathStr, StrUtil.SPACE)) {
            return "\"" + pathStr + "\"";
        }
        return pathStr;
    }

    /**
     * 增量刷新控制台traceLog（从文件上次读取的字节偏移量继续读取，避免每次全量加载导致内存暴涨和卡顿）
     * 异步在后台线程池执行文件读取，避免阻塞 EDT UI 线程
     *
     * @param project:
     **/
    public static void refreshConsoleLog(Project project) {
        if (project == null || project.isDisposed()) {
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (project.isDisposed()) {
                return;
            }
            String projectName = project.getName();
            String agentJarPath = getAgentCoreJarPath2();
            if (StrUtil.isEmpty(agentJarPath)) {
                return;
            }
            String logFilePath = FileCache.getLogFilePath(agentJarPath, projectName);
            if (logFilePath == null) {
                return;
            }
            File logFile = new File(logFilePath);
            if (!logFile.exists() || logFile.length() == 0) {
                return;
            }

            ConsoleView consoleView = MonitorOutputToolWindow.getConsoleView(project);
            if (consoleView == null) {
                return;
            }

            Long lastOffset = lastFileOffsetCache.get(projectName);
            if (lastOffset == null) {
                lastOffset = 0L;
            }
            long fileLength = logFile.length();
            if (fileLength < lastOffset) {
                lastOffset = 0L;
            }
            if (fileLength == lastOffset) {
                return;
            }

            List<TraceConsoleDTO> dtoList = new java.util.ArrayList<>();
            long newOffset = lastOffset;
            try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
                raf.seek(lastOffset);
                String line;
                while ((line = raf.readLine()) != null) {
                    byte[] bytes = line.getBytes(StandardCharsets.ISO_8859_1);
                    String utf8Line = new String(bytes, StandardCharsets.UTF_8).trim();
                    if (StrUtil.isNotBlank(utf8Line)) {
                        try {
                            TraceConsoleDTO dto = JSONUtil.toBean(utf8Line, TraceConsoleDTO.class);
                            if (dto != null && dto.getText() != null) {
                                dtoList.add(dto);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
                newOffset = raf.getFilePointer();
            } catch (Exception ignored) {
            }

            if (!dtoList.isEmpty()) {
                lastFileOffsetCache.put(projectName, newOffset);
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (project.isDisposed()) {
                        return;
                    }
                    int skipTokens = 0;
                    for (TraceConsoleDTO dto : dtoList) {
                        if (skipTokens > 0) {
                            skipTokens--;
                            continue;
                        }
                        if (isJavaClassLog(dto.getText())) {
                            skipTokens = 2;
                            continue;
                        }
                        printConsole(consoleView, dto);
                    }
                    MonitorOutputToolWindow.onLogUpdated(project);
                }, ModalityState.defaultModalityState());
            }
        });
    }

    /**
     * 判断日志文本是否包含 Java 系统类调用（如 java.*, javax.*, sun.*, com.sun.*, jdk.*）
     */
    private static boolean isJavaClassLog(String text) {
        if (text == null) {
            return false;
        }
        int start = text.indexOf("【");
        int end = text.indexOf("】");
        if (start != -1 && end != -1 && end > start) {
            String afterTag = text.substring(end + 1).trim();
            return afterTag.startsWith("java.")
                    || afterTag.startsWith("javax.")
                    || afterTag.startsWith("sun.")
                    || afterTag.startsWith("com.sun.")
                    || afterTag.startsWith("jdk.");
        }
        return false;
    }

    public static void showAllConsoleLog(Project project) {
        if (project != null) {
            lastFileOffsetCache.put(project.getName(), 0L);
            refreshConsoleLog(project);
        }
    }

    /**
     * 控制台打印trace内容
     *
     * @param consoleView:
     * @param consoleDTO:
     **/
    private static void printConsole(ConsoleView consoleView, TraceConsoleDTO consoleDTO) {
        if (consoleView == null || consoleDTO == null) {
            return;
        }
        String showText = consoleDTO.getText();
        if (showText == null) {
            return;
        }
        String color = consoleDTO.getColor();
        ConsoleViewContentType contentType;
        if (color == null) {
            contentType = DARK_GRAY_OUTPUT;
        } else if (AgentConstant.CONSOLE_COLOR_CYAN.equals(color)) {
            contentType = CYAN_OUTPUT;
        } else if (AgentConstant.CONSOLE_COLOR_GREEN.equals(color)) {
            contentType = GREEN_OUTPUT;
        } else if (AgentConstant.CONSOLE_COLOR_RED.equals(color)) {
            contentType = RED_OUTPUT;
        } else {
            contentType = ConsoleViewContentType.NORMAL_OUTPUT;
        }
        consoleView.print(showText, contentType);
    }

}