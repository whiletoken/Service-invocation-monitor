package com.caijy.plugin.ui;

import com.caijy.plugin.utils.PluginUtil;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 带有实时检索工具栏与操作侧栏的 Monitor 控制台面板
 *
 * @author liuju
 * @date 2026/10/17
 */
public class MonitorConsolePanel extends SimpleToolWindowPanel implements Disposable, DumbAware {

    private final Project project;
    private final ConsoleView consoleView;

    private final JPanel searchPanel;
    private final SearchTextField searchField;
    private final JBLabel countLabel;
    private final JButton prevButton;
    private final JButton nextButton;
    private final JButton closeButton;

    private final List<RangeHighlighter> highlighters = new ArrayList<>();
    private final List<Integer> matchStarts = new ArrayList<>();
    private final List<Integer> matchEnds = new ArrayList<>();
    private int currentMatchIndex = -1;

    public MonitorConsolePanel(@NotNull Project project, @NotNull ConsoleView consoleView) {
        super(false, true);
        this.project = project;
        this.consoleView = consoleView;

        // 1. 构建搜索栏组件
        searchPanel = new JPanel(new BorderLayout(JBUI.scale(6), 0));
        searchPanel.setBorder(JBUI.Borders.empty(4, 6));

        searchField = new SearchTextField(true);
        searchField.getTextEditor().getEmptyText().setText("Search stack trace / 搜索调用链路 (Enter 下一个, Shift+Enter 上一个)...");
        searchField.getTextEditor().setColumns(28);

        countLabel = new JBLabel("");
        countLabel.setForeground(JBColor.GRAY);
        countLabel.setBorder(JBUI.Borders.empty(0, 4));

        prevButton = new JButton(AllIcons.Actions.PreviousOccurence);
        prevButton.setToolTipText("Previous occurrence / 上一个匹配项 (Shift+Enter)");
        prevButton.setFocusable(false);
        prevButton.setBorderPainted(false);
        prevButton.setContentAreaFilled(false);
        prevButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        prevButton.addActionListener(e -> navigateMatch(-1));

        nextButton = new JButton(AllIcons.Actions.NextOccurence);
        nextButton.setToolTipText("Next occurrence / 下一个匹配项 (Enter)");
        nextButton.setFocusable(false);
        nextButton.setBorderPainted(false);
        nextButton.setContentAreaFilled(false);
        nextButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        nextButton.addActionListener(e -> navigateMatch(1));

        closeButton = new JButton(AllIcons.Actions.Cancel);
        closeButton.setToolTipText("Close search / 关闭搜索 (Esc)");
        closeButton.setFocusable(false);
        closeButton.setBorderPainted(false);
        closeButton.setContentAreaFilled(false);
        closeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeButton.addActionListener(e -> {
            clearSearch();
            searchPanel.setVisible(false);
            Editor editor = getEditor();
            if (editor != null && editor.getContentComponent() != null) {
                editor.getContentComponent().requestFocusInWindow();
            }
        });

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(2), 0));
        rightControls.add(countLabel);
        rightControls.add(prevButton);
        rightControls.add(nextButton);
        rightControls.add(closeButton);

        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(rightControls, BorderLayout.EAST);

        // 默认显示搜索栏
        searchPanel.setVisible(true);

        // 搜索输入监听
        searchField.addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                performSearch();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                performSearch();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                performSearch();
            }
        });

        // 键盘快捷键监听
        searchField.getTextEditor().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (e.isShiftDown()) {
                        navigateMatch(-1);
                    } else {
                        navigateMatch(1);
                    }
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    clearSearch();
                    searchPanel.setVisible(false);
                    Editor editor = getEditor();
                    if (editor != null && editor.getContentComponent() != null) {
                        editor.getContentComponent().requestFocusInWindow();
                    }
                    e.consume();
                }
            }
        });

        // 2. 组装中间控制台区域
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(searchPanel, BorderLayout.NORTH);
        centerPanel.add(consoleView.getComponent(), BorderLayout.CENTER);
        setContent(centerPanel);

        // 3. 构建左侧工具栏 (ActionToolbar)
        DefaultActionGroup actionGroup = new DefaultActionGroup();

        // 搜索 Action (快捷键 Ctrl+F)
        AnAction findAction = new DumbAwareAction("Find Stack Trace", "Search in stack trace console / 搜索调用链路 (Ctrl+F)", AllIcons.Actions.Find) {
            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                focusSearchField();
            }
        };
        findAction.registerCustomShortcutSet(
                ActionManager.getInstance().getAction(IdeActions.ACTION_FIND).getShortcutSet(),
                centerPanel
        );
        actionGroup.add(findAction);

        // 重新加载全部日志 Action
        AnAction refreshAction = new DumbAwareAction("Show All Trace Log", "Reload all trace logs / 重新加载全部日志", AllIcons.Actions.Refresh) {
            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                PluginUtil.showAllConsoleLog(project);
            }
        };
        actionGroup.add(refreshAction);

        // 清空控制台 Action
        AnAction clearAction = new DumbAwareAction("Clear Console", "Clear console output / 清空控制台", AllIcons.Actions.GC) {
            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                consoleView.clear();
                clearSearch();
            }
        };
        actionGroup.add(clearAction);

        // 控制台自带的操作 (例如滚动到底部、软换行等)
        AnAction[] consoleActions = consoleView.createConsoleActions();
        if (consoleActions != null) {
            for (AnAction a : consoleActions) {
                if (a != null && !a.getClass().getSimpleName().contains("ClearAll")) {
                    actionGroup.add(a);
                }
            }
        }

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("MonitorConsoleToolbar", actionGroup, false);
        toolbar.setTargetComponent(consoleView.getComponent());
        setToolbar(toolbar.getComponent());
    }

    /**
     * 获取 ConsoleView 底层的 Editor 实例
     */
    public Editor getEditor() {
        if (consoleView instanceof com.intellij.execution.impl.ConsoleViewImpl) {
            return ((com.intellij.execution.impl.ConsoleViewImpl) consoleView).getEditor();
        }
        if (consoleView != null && consoleView.getComponent() != null) {
            return CommonDataKeys.EDITOR.getData(
                    DataManager.getInstance().getDataContext(consoleView.getComponent())
            );
        }
        return null;
    }

    /**
     * 激活并聚焦搜索框
     */
    public void focusSearchField() {
        searchPanel.setVisible(true);
        Editor editor = getEditor();
        if (editor != null) {
            String selectedText = editor.getSelectionModel().getSelectedText();
            if (selectedText != null && !selectedText.trim().isEmpty() && !selectedText.contains("\n")) {
                searchField.setText(selectedText.trim());
            }
        }
        searchField.getTextEditor().selectAll();
        searchField.getTextEditor().requestFocusInWindow();
    }

    /**
     * 执行高亮搜索
     */
    public void performSearch() {
        Editor editor = getEditor();
        if (editor == null) {
            return;
        }
        clearHighlights();
        String query = searchField.getText();
        if (query == null || query.trim().isEmpty()) {
            countLabel.setText("");
            return;
        }
        String queryLower = query.toLowerCase();
        CharSequence docText = editor.getDocument().getCharsSequence();
        String text = docText.toString().toLowerCase();

        int index = 0;
        while ((index = text.indexOf(queryLower, index)) != -1) {
            matchStarts.add(index);
            matchEnds.add(index + query.length());
            index += query.length();
        }

        if (matchStarts.isEmpty()) {
            countLabel.setText("0 matches");
            countLabel.setForeground(JBColor.RED);
            currentMatchIndex = -1;
            return;
        }

        countLabel.setForeground(JBColor.GRAY);
        MarkupModel markupModel = editor.getMarkupModel();
        TextAttributes attributes = EditorColorsManager.getInstance()
                .getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
        if (attributes == null) {
            attributes = new TextAttributes(null, new JBColor(0xFFE599, 0x5C4A1F), null, null, 0);
        }

        for (int i = 0; i < matchStarts.size(); i++) {
            RangeHighlighter h = markupModel.addRangeHighlighter(
                    matchStarts.get(i),
                    matchEnds.get(i),
                    HighlighterLayer.SELECTION - 1,
                    attributes,
                    HighlighterTargetArea.EXACT_RANGE
            );
            highlighters.add(h);
        }

        int caretOffset = editor.getCaretModel().getOffset();
        currentMatchIndex = 0;
        for (int i = 0; i < matchStarts.size(); i++) {
            if (matchStarts.get(i) >= caretOffset) {
                currentMatchIndex = i;
                break;
            }
        }
        highlightCurrentMatch(editor);
    }

    private void highlightCurrentMatch(Editor editor) {
        if (currentMatchIndex < 0 || currentMatchIndex >= matchStarts.size()) {
            return;
        }
        int start = matchStarts.get(currentMatchIndex);
        int end = matchEnds.get(currentMatchIndex);

        editor.getCaretModel().moveToOffset(start);
        editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
        editor.getSelectionModel().setSelection(start, end);

        countLabel.setText((currentMatchIndex + 1) + " of " + matchStarts.size());
    }

    /**
     * 导航到上一个或下一个匹配项
     *
     * @param direction 1 表示下一个，-1 表示上一个
     */
    public void navigateMatch(int direction) {
        if (matchStarts.isEmpty()) {
            return;
        }
        Editor editor = getEditor();
        if (editor == null) {
            return;
        }
        currentMatchIndex += direction;
        if (currentMatchIndex >= matchStarts.size()) {
            currentMatchIndex = 0;
        } else if (currentMatchIndex < 0) {
            currentMatchIndex = matchStarts.size() - 1;
        }
        highlightCurrentMatch(editor);
    }

    /**
     * 清空搜索内容和高亮
     */
    public void clearSearch() {
        searchField.setText("");
        clearHighlights();
        countLabel.setText("");
    }

    /**
     * 清理所有搜索高亮
     */
    public void clearHighlights() {
        Editor editor = getEditor();
        if (editor != null && !highlighters.isEmpty()) {
            MarkupModel markupModel = editor.getMarkupModel();
            for (RangeHighlighter h : highlighters) {
                try {
                    markupModel.removeHighlighter(h);
                } catch (Exception ignored) {
                }
            }
        }
        highlighters.clear();
        matchStarts.clear();
        matchEnds.clear();
        currentMatchIndex = -1;
    }

    /**
     * 日志更新后的回调通知，若当前在检索状态则刷新检索结果
     */
    public void onLogUpdated() {
        if (searchField.getText() != null && !searchField.getText().trim().isEmpty()) {
            performSearch();
        }
    }

    @Override
    public void dispose() {
        clearHighlights();
        Disposer.dispose(consoleView);
    }
}
