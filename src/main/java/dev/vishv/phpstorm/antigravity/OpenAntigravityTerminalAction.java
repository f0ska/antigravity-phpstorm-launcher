package dev.vishv.phpstorm.antigravity;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.terminal.backend.TerminalSessionPersistedTab;
import com.intellij.terminal.backend.TerminalTabsStorage;
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabBuilder;
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab;
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager;
import com.intellij.terminal.frontend.view.TerminalView;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.startup.TerminalProcessType;
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration;

import java.util.List;

public final class OpenAntigravityTerminalAction extends DumbAwareAction {
    private static final String TAB_NAME = "Antigravity";
    private static final String TERMINAL_TOOL_WINDOW_ID = "terminal";
    private static final String COMMAND = "agy";
    private static final Key<Boolean> CLEANUP_REGISTERED_KEY = Key.create("dev.vishv.phpstorm.antigravity.cleanupRegistered");
    private static final Key<Boolean> START_COMMAND_SENT_KEY = Key.create("dev.vishv.phpstorm.antigravity.startCommandSent");

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(event.getProject() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }

        ensureCleanupRegistered(project);
        removeStoredAntigravityTabs(project);

        TerminalToolWindowTabsManager tabsManager = TerminalToolWindowTabsManager.getInstance(project);
        TerminalToolWindowTab existingTab = findAntigravityTab(tabsManager);
        if (existingTab != null) {
            focusTab(project, existingTab);
            if (isExecutingCommand(existingTab)) {
                sendCurrentFilePath(project, existingTab.getView());
            }
            scheduleStorageCleanup(project);
            return;
        }

        TerminalToolWindowTabBuilder builder = TerminalToolWindowTabsManager.getInstance(project)
            .createTabBuilder()
            .tabName(TAB_NAME)
            .workingDirectory(getProjectRoot(project))
            .processType(TerminalProcessType.SHELL)
            .closeOnProcessTermination(false)
            .requestFocus(true);

        TerminalToolWindowTab tab = builder.createTab();
        sendStartCommandWhenReady(project, tab);

        scheduleStorageCleanup(project);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private static @NotNull String getProjectRoot(@NotNull Project project) {
        String basePath = project.getBasePath();
        return basePath == null ? System.getProperty("user.home") : basePath;
    }

    private static TerminalToolWindowTab findAntigravityTab(@NotNull TerminalToolWindowTabsManager tabsManager) {
        return tabsManager.getTabs().stream()
            .filter(tab -> TAB_NAME.equals(tab.getContent().getDisplayName()))
            .findFirst()
            .orElse(null);
    }

    private static void focusTab(@NotNull Project project, @NotNull TerminalToolWindowTab tab) {
        ContentManager contentManager = tab.getContent().getManager();
        if (contentManager != null) {
            contentManager.setSelectedContent(tab.getContent(), true, true);
        }

        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TERMINAL_TOOL_WINDOW_ID);
        if (toolWindow != null) {
            toolWindow.activate(() -> {
                ContentManager manager = tab.getContent().getManager();
                if (manager != null) {
                    manager.requestFocus(tab.getContent(), true);
                }
            }, true);
        }
    }

    private static boolean isExecutingCommand(@NotNull TerminalToolWindowTab tab) {
        try {
            TerminalShellIntegration shellIntegration = tab.getView().getShellIntegrationDeferred().getCompleted();
            String statusClassName = shellIntegration.getOutputStatus().getValue().getClass().getName();
            return statusClassName.endsWith("TerminalOutputStatus$ExecutingCommand");
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private static void sendCurrentFilePath(@NotNull Project project, @NotNull TerminalView view) {
        VirtualFile file = FileEditorManager.getInstance(project).getCurrentFile();
        if (file == null || file.isDirectory()) {
            return;
        }

        view.createSendTextBuilder()
            .useBracketedPasteMode()
            .send(getDisplayPath(project, file));
    }

    private static @NotNull String getDisplayPath(@NotNull Project project, @NotNull VirtualFile file) {
        String basePath = project.getBasePath();
        String filePath = file.getPath();
        if (basePath == null) {
            return filePath;
        }

        String prefix = basePath.endsWith("/") ? basePath : basePath + "/";
        return filePath.startsWith(prefix) ? filePath.substring(prefix.length()) : filePath;
    }

    private static void sendStartCommandWhenReady(@NotNull Project project, @NotNull TerminalToolWindowTab tab) {
        Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project);
        alarm.addRequest(() -> sendStartCommand(tab), 300);
        alarm.addRequest(() -> sendStartCommand(tab), 1_000);
        alarm.addRequest(() -> sendStartCommand(tab), 2_500);
    }

    private static void sendStartCommand(@NotNull TerminalToolWindowTab tab) {
        if (Boolean.TRUE.equals(tab.getContent().getUserData(START_COMMAND_SENT_KEY))) {
            return;
        }

        try {
            tab.getView().createSendTextBuilder()
                .shouldExecute()
                .send(COMMAND);
            tab.getContent().putUserData(START_COMMAND_SENT_KEY, true);
        } catch (IllegalStateException ignored) {
        }
    }

    private static void ensureCleanupRegistered(@NotNull Project project) {
        if (Boolean.TRUE.equals(project.getUserData(CLEANUP_REGISTERED_KEY))) {
            return;
        }

        project.putUserData(CLEANUP_REGISTERED_KEY, true);
        Disposer.register(project, (Disposable) () -> removeStoredAntigravityTabs(project));
    }

    private static void scheduleStorageCleanup(@NotNull Project project) {
        ApplicationManager.getApplication().invokeLater(() -> removeStoredAntigravityTabs(project), project.getDisposed());

        Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project);
        alarm.addRequest(() -> removeStoredAntigravityTabs(project), 1_000);
        alarm.addRequest(() -> removeStoredAntigravityTabs(project), 5_000);
        alarm.addRequest(() -> removeStoredAntigravityTabs(project), 15_000);
    }

    private static void removeStoredAntigravityTabs(@NotNull Project project) {
        TerminalTabsStorage storage = TerminalTabsStorage.getInstance(project);
        List<TerminalSessionPersistedTab> tabs = storage.getStoredTabs();
        List<TerminalSessionPersistedTab> filteredTabs = tabs.stream()
            .filter(tab -> !TAB_NAME.equals(tab.getName()))
            .toList();

        if (filteredTabs.size() != tabs.size()) {
            storage.updateStoredTabs(filteredTabs);
        }
    }
}
