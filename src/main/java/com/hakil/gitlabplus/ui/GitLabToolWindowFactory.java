package com.hakil.gitlabplus.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * 注册「GitLab+」工具窗口。
 */
public final class GitLabToolWindowFactory implements ToolWindowFactory {

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    GitLabMergePanel panel = new GitLabMergePanel(project);
    Content content = ContentFactory.getInstance().createContent(panel.component(), "Merge Requests", false);
    // 面板持有消息总线订阅，需要随 content 一起释放
    com.intellij.openapi.util.Disposer.register(content, panel);
    toolWindow.getContentManager().addContent(content);
  }

  @Override
  public boolean shouldBeAvailable(@NotNull Project project) {
    return true;
  }
}
