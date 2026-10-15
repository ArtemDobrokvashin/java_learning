package io.javada.idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/** Registers a project-scoped tool window; does not start or install Flask. */
public final class TrainerToolWindowFactory implements ToolWindowFactory {
    @Override public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        TrainerPanel panel = new TrainerPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel.component(), "Задачи", false);
        content.setDisposer(panel);
        toolWindow.getContentManager().addContent(content);
    }
}
