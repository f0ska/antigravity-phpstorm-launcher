package dev.vishv.phpstorm.antigravity;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.terminal.backend.TerminalSessionPersistedTab;
import com.intellij.terminal.backend.TerminalTabsStorage;
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabBuilder;
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab;
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.startup.TerminalProcessType;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public final class OpenAntigravityTerminalAction extends DumbAwareAction {
    private static final String TAB_NAME = "Antigravity";
    private static final String COMMAND = "agy";
    private static final Key<Boolean> CLEANUP_REGISTERED_KEY = Key.create("dev.vishv.phpstorm.antigravity.cleanupRegistered");

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

        TerminalToolWindowTabBuilder builder = TerminalToolWindowTabsManager.getInstance(project)
            .createTabBuilder()
            .tabName(TAB_NAME)
            .workingDirectory(getProjectRoot(project))
            .shellCommand(Arrays.asList("/usr/bin/env", COMMAND))
            .processType(TerminalProcessType.NON_SHELL)
            .requestFocus(true);

        closeOnProcessTermination(builder);
        TerminalToolWindowTab tab = builder.createTab();

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

    private static void closeOnProcessTermination(@NotNull TerminalToolWindowTabBuilder builder) {
        try {
            Method method = builder.getClass().getMethod("closeOnProcessTermination", boolean.class);
            method.invoke(builder, true);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
