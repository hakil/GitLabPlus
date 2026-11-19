package com.hakil.gitlabplus.ui;

import com.hakil.gitlabplus.i18n.GitLabPlusBundle;
import com.hakil.gitlabplus.model.MergeCommitMessages;
import com.hakil.gitlabplus.model.MergeRequestDetails;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nullable;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * 合并对话框（对齐官方 merge 对话框的信息面）：
 * 显示分支、可编辑合并提交信息、squash 与「合并后删除源分支」开关。
 */
public final class MergeDialog extends DialogWrapper {

  private final MergeRequestDetails details;
  private final boolean forceSquash;
  private final JTextArea commitMessageArea = new JTextArea(4, 60);
  private final JCheckBox deleteSourceCheck =
      new JCheckBox(GitLabPlusBundle.message("merge.removeSource"));
  private final JCheckBox squashCheck = new JCheckBox(GitLabPlusBundle.message("merge.squash"));
  private final JLabel commitHintLabel = new JLabel();

  /**
   * @param forceSquash true 表示从「Squash and Merge」进入，squash 勾选不可取消
   */
  public MergeDialog(Project project, MergeRequestDetails details, boolean forceSquash) {
    super(project, true);
    this.details = details;
    this.forceSquash = forceSquash;
    boolean squashByDefault = MergeCommitMessages.defaultSquash(forceSquash, details.squashOnMerge);
    // 默认留空：GitLab 会把自定义提交信息追加到它生成的标题之后，预填同款文本就会重复
    commitMessageArea.setText(MergeCommitMessages.initialMessage());
    commitHintLabel.setText(GitLabPlusBundle.message("merge.commitMessageHint") + " "
                                + MergeCommitMessages.defaultMessage(squashByDefault,
                                                                    details.defaultSquashCommitMessage,
                                                                    details.defaultMergeCommitMessage,
                                                                    details.title));
    commitMessageArea.setLineWrap(true);
    commitMessageArea.setWrapStyleWord(true);
    deleteSourceCheck.setSelected(details.shouldRemoveSourceBranch);
    deleteSourceCheck.setEnabled(!details.forceRemoveSourceBranch);
    if (details.forceRemoveSourceBranch) {
      deleteSourceCheck.setText(GitLabPlusBundle.message("merge.removeSourceForced"));
    }
    else {
      deleteSourceCheck.setText(GitLabPlusBundle.message("merge.removeSourceWithBranch", details.sourceBranch));
    }
    // 初始状态跟随 MR 自身的 squash 设置，不硬编码勾选
    squashCheck.setSelected(squashByDefault);
    squashCheck.setEnabled(MergeCommitMessages.squashEditable(forceSquash, details.squashReadOnly));
    setTitle(forceSquash
                 ? GitLabPlusBundle.message("merge.titleSquash", details.iid)
                 : GitLabPlusBundle.message("merge.titleMerge", details.iid));
    init();
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    JPanel info = new JPanel();
    info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
    info.add(new JLabel(GitLabPlusBundle.message("merge.label.title") + details.title));
    info.add(new JLabel(GitLabPlusBundle.message("merge.label.branches") + details.displayBranch()));
    info.add(new JLabel(GitLabPlusBundle.message("merge.label.status") + details.statusSummary()));

    JPanel messagePanel = new JPanel(new BorderLayout(0, 2));
    JPanel messageHeader = new JPanel();
    messageHeader.setLayout(new BoxLayout(messageHeader, BoxLayout.Y_AXIS));
    messageHeader.add(new JLabel(GitLabPlusBundle.message("merge.commitMessage")));
    messageHeader.add(commitHintLabel);
    messagePanel.add(messageHeader, BorderLayout.NORTH);
    messagePanel.add(new JBScrollPane(commitMessageArea), BorderLayout.CENTER);

    JPanel options = new JPanel();
    options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
    if (!forceSquash) {
      options.add(squashCheck);
    }
    options.add(deleteSourceCheck);

    JPanel root = new JPanel(new BorderLayout(0, 8));
    root.add(info, BorderLayout.NORTH);
    root.add(messagePanel, BorderLayout.CENTER);
    root.add(options, BorderLayout.SOUTH);
    root.setPreferredSize(new Dimension(620, 320));
    return root;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return commitMessageArea;
  }

  public String commitMessage() {
    String text = commitMessageArea.getText();
    return text == null ? "" : text.trim();
  }

  /** squash 时返回压缩提交信息，否则返回 {@code null}（不发送，避免同一标题写两次）。 */
  public String squashCommitMessage() {
    return MergeCommitMessages.squash(squash(), commitMessage());
  }

  public boolean squash() {
    return forceSquash || squashCheck.isSelected();
  }

  public boolean deleteSourceBranch() {
    return deleteSourceCheck.isSelected();
  }
}
