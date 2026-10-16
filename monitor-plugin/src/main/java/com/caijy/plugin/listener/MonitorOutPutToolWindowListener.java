package com.caijy.plugin.listener;

import com.caijy.plugin.ui.MonitorOutputToolWindow;
import com.caijy.plugin.utils.PluginUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import org.jetbrains.annotations.NotNull;

/**
 * @author liguang
 * @date 2023/1/5 星期四 3:34 下午
 */
public class MonitorOutPutToolWindowListener implements ToolWindowManagerListener {

    private final Project project;

    public MonitorOutPutToolWindowListener(Project project) {
        this.project = project;
    }

    @Override
    public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
        if (project == null || project.isDisposed()) {
            return;
        }
        if (MonitorOutputToolWindow.ID.equals(toolWindowManager.getActiveToolWindowId())) {
            PluginUtil.refreshConsoleLog(project);
        }
    }
}
