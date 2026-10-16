package com.caijy.plugin.ui;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author liguang
 * @date 2022/12/7 星期三 8:18 下午
 */
public class MonitorOutputToolWindow implements ToolWindowFactory, DumbAware {
    private static final Map<Project, ConsoleView> consoleViews = new ConcurrentHashMap<>();
    private static final Map<Project, MonitorConsolePanel> consolePanels = new ConcurrentHashMap<>();

    public static final String ID = "Monitor";

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ConsoleView consoleView = consoleViews.get(project);
        if (consoleView == null) {
            createToolWindow(project, toolWindow);
        }
        toolWindow.activate(null, false);
    }

    // 为了方便其他地方使用 ConsoleView ，定义该方法获取 ConsoleView
    // ConsoleView 对象通过其 print 方法，向自定义的控制台窗口输出文本内容
    public static ConsoleView getConsoleView(Project project) {
        if (project == null || project.isDisposed()) {
            return null;
        }
        // 创建过了就不再重复创建了
        ConsoleView consoleView = consoleViews.get(project);
        if (consoleView == null) {
            ToolWindow toolWindow = getToolWindow(project);
            if (toolWindow != null) {
                consoleView = createToolWindow(project, toolWindow);
            }
        }
        return consoleView;
    }

    public static MonitorConsolePanel getConsolePanel(Project project) {
        if (project == null || project.isDisposed()) {
            return null;
        }
        return consolePanels.get(project);
    }

    /**
     * 日志增量刷新后触发检索高亮同步
     */
    public static void onLogUpdated(Project project) {
        if (project == null || project.isDisposed()) {
            return;
        }
        MonitorConsolePanel panel = consolePanels.get(project);
        if (panel != null) {
            panel.onLogUpdated();
        }
    }

    // 设置工具栏窗口显示的 UI 为控制台窗口视图，并集成搜索与侧边操作栏
    private static synchronized ConsoleView createToolWindow(Project project, ToolWindow toolWindow) {
        ConsoleView existing = consoleViews.get(project);
        if (existing != null) {
            return existing;
        }
        TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
        ConsoleView consoleView = builder.getConsole();
        consoleViews.put(project, consoleView);

        MonitorConsolePanel consolePanel = new MonitorConsolePanel(project, consoleView);
        consolePanels.put(project, consolePanel);

        // 注册到项目生命周期，当 Project 关闭时自动释放 ConsolePanel 与 ConsoleView，防止 IDEA 报告 Project 内存泄露
        Disposer.register(project, () -> {
            consoleViews.remove(project);
            consolePanels.remove(project);
            Disposer.dispose(consolePanel);
        });

        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(consolePanel,
            "Monitor Console Output", false);
        content.getComponent().setVisible(true);
        content.setCloseable(false);
        toolWindow.getContentManager().addContent(content);

        return consoleView;
    }

    public static ToolWindow getToolWindow(@NotNull Project project) {
        return ToolWindowManager.getInstance(project).getToolWindow(ID);
    }
}
