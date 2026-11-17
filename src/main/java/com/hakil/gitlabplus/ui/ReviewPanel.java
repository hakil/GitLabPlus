package com.hakil.gitlabplus.ui;

import com.hakil.gitlabplus.api.GitLabClient;
import com.hakil.gitlabplus.i18n.GitLabPlusBundle;
import com.hakil.gitlabplus.model.Discussion;
import com.hakil.gitlabplus.service.GitLabReviewService;
import com.hakil.gitlabplus.service.GitLabService;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import java.util.function.Supplier;

/**
 * 评审面板（P2）：讨论列表、回复、resolve、reaction、草稿评论与 Submit review。
 *
 * <p>对齐官方插件评审能力的操作集合；端点与官方一致（GitLab REST v4）。</p>
 */
public final class ReviewPanel {

  private static final String NOTIFICATION_GROUP = "GitLabPlus.Notifications";

  private final Project project;
  private final Supplier<GitLabService> serviceSupplier;
  private final Supplier<String> projectPathSupplier;

  private final JPanel root = new JPanel(new BorderLayout(0, 4));
  private final DefaultListModel<Discussion> listModel = new DefaultListModel<>();
  private final JList<Discussion> list = new JList<>(listModel);
  private final javax.swing.JTextPane detailPane = new javax.swing.JTextPane();
  private final JButton reloadButton = new JButton(GitLabPlusBundle.message("review.reload"));
  private final JButton resolveButton = new JButton(GitLabPlusBundle.message("review.resolve"));
  private final JButton unresolveButton = new JButton(GitLabPlusBundle.message("review.unresolve"));
  private final JButton thumbsUpButton = new JButton(GitLabPlusBundle.message("review.thumbsUp"));
  private final JButton replyButton = new JButton(GitLabPlusBundle.message("review.reply"));
  private final JButton commentButton = new JButton(GitLabPlusBundle.message("review.comment"));
  /** 默认只看真实讨论；勾上才显示 add commit / changed description 这类系统动态。 */
  private final JCheckBox showActivityCheck = new JCheckBox(GitLabPlusBundle.message("review.showActivity"));
  private final JLabel statusLabel = new JLabel(" ");
  /** 最近一次拉到的全部讨论（切换过滤不需要重新请求）。 */
  private List<Discussion> loaded = List.of();

  private @Nullable String iid;
  private boolean busy;

  public ReviewPanel(Project project,
                     Supplier<GitLabService> serviceSupplier,
                     Supplier<String> projectPathSupplier) {
    this.project = project;
    this.serviceSupplier = serviceSupplier;
    this.projectPathSupplier = projectPathSupplier;
    buildUi();
  }

  public JComponent component() {
    return root;
  }

  private void buildUi() {
    detailPane.setContentType("text/html");
    detailPane.setEditable(false);
    detailPane.setBackground(UIUtil.getPanelBackground());

    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList<?> jList, Object value, int index,
                                                    boolean selected, boolean focused) {
        String text = value instanceof Discussion d
            ? (d.resolved() ? "[已解决] " : "") + d.firstAuthor() + "：" + oneLine(d.firstBody(), 70)
            : String.valueOf(value);
        return super.getListCellRendererComponent(jList, text, index, selected, focused);
      }
    });
    list.addListSelectionListener(e -> renderSelected());

    JPanel listPanel = new JPanel(new BorderLayout(0, 2));
    listPanel.add(new JLabel(GitLabPlusBundle.message("review.label.discussions")), BorderLayout.NORTH);
    listPanel.add(new JBScrollPane(list), BorderLayout.CENTER);
    listPanel.setPreferredSize(new Dimension(240, 140));
    listPanel.setMinimumSize(new Dimension(140, 60));

    JPanel discussionActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
    discussionActions.add(reloadButton);
    discussionActions.add(thumbsUpButton);
    discussionActions.add(resolveButton);
    discussionActions.add(unresolveButton);
    discussionActions.add(showActivityCheck);

    JPanel detailPanel = new JPanel(new BorderLayout(0, 2));
    detailPanel.add(new JLabel(GitLabPlusBundle.message("review.label.selected")), BorderLayout.NORTH);
    detailPanel.add(new JBScrollPane(detailPane), BorderLayout.CENTER);

    // 输入区改为按按钮触发：两个常驻输入框会与列表、详情争高度
    JPanel actionsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
    actionsRow.add(replyButton);
    actionsRow.add(commentButton);
    actionsRow.add(statusLabel);

    JPanel body = new JPanel(new BorderLayout(0, 4));
    body.add(listPanel, BorderLayout.WEST);
    body.add(detailPanel, BorderLayout.CENTER);

    root.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    root.add(discussionActions, BorderLayout.NORTH);
    root.add(body, BorderLayout.CENTER);
    root.add(actionsRow, BorderLayout.SOUTH);

    reloadButton.addActionListener(e -> reload());
    resolveButton.addActionListener(e -> resolve(true));
    unresolveButton.addActionListener(e -> resolve(false));
    thumbsUpButton.addActionListener(e -> thumbsUp());
    replyButton.addActionListener(e -> reply());
    commentButton.addActionListener(e -> addComment());
    showActivityCheck.addActionListener(e -> refillList());
    showActivityCheck.setToolTipText("显示 add commit / changed description 等系统动态（默认隐藏）");
    updateButtons();
  }

  // ------------------------------------------------------------------ 数据

  public void load(@Nullable String iid) {
    this.iid = iid;
    listModel.clear();
    detailPane.setText("");
    updateButtons();
    if (iid == null || iid.isBlank()) {
      return;
    }
    reload();
  }

  private void reload() {
    GitLabReviewService review = review();
    String path = projectPath();
    String currentIid = iid;
    if (review == null || path == null || currentIid == null) {
      return;
    }
    setBusy(true, GitLabPlusBundle.message("status.loading", GitLabPlusBundle.message("noun.discussions")));
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        List<Discussion> discussions = review.listDiscussions(path, currentIid);
        UIUtil.invokeLaterIfNeeded(() -> {
          loaded = discussions;
          setBusy(false, GitLabPlusBundle.message("status.discussionsCount", discussions.size()));
          refillList();
          renderSelected();
        });
      }
      catch (Exception t) {
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, GitLabPlusBundle.message("status.loadFailed", GitLabPlusBundle.message("noun.discussions")));
          showError("加载讨论失败", t);
        });
      }
    });
  }

  /** 按「是否显示动态」重建列表，并尽量保持选中项。 */
  private void refillList() {
    Discussion previous = list.getSelectedValue();
    String previousId = previous == null ? null : previous.id;
    listModel.clear();
    int hidden = 0;
    int restored = -1;
    for (Discussion discussion : loaded) {
      if (!showActivityCheck.isSelected() && discussion.isActivity()) {
        hidden++;
        continue;
      }
      if (previousId != null && previousId.equals(discussion.id)) {
        restored = listModel.size();
      }
      listModel.addElement(discussion);
    }
    if (restored >= 0) {
      list.setSelectedIndex(restored);
    }
    else if (!listModel.isEmpty() && previous != null) {
      list.setSelectedIndex(0);
    }
    if (hidden > 0) {
      statusLabel.setText("已隐藏 " + hidden + " 条系统动态（勾选「显示动态」可查看）");
    }
    updateButtons();
  }

  private void renderSelected() {
    Discussion selected = list.getSelectedValue();
    if (selected == null) {
      detailPane.setText("<html><body style='font-family:sans-serif;font-size:11px'>选择左侧讨论查看详情</body></html>");
      updateButtons();
      return;
    }
    StringBuilder sb = new StringBuilder("<html><body style='font-family:sans-serif;font-size:11px'>");
    for (Discussion.Note note : selected.notes) {
      sb.append("<div><b>").append(escape(note.author)).append("</b>")
          .append(note.system ? " <i>(system)</i>" : "")
          .append(" <span>").append(escape(note.createdAt)).append("</span>")
          .append(note.resolvable ? (note.resolved ? " <b>[resolved]</b>" : " <i>[unresolved]</i>") : "")
          .append(!note.awardSummary().isEmpty() ? " <i>" + escape(note.awardSummary()) + "</i>" : "")
          .append("</div><div>").append(escape(note.body).replace("\n", "<br>")).append("</div><hr>");
    }
    sb.append("</body></html>");
    detailPane.setText(sb.toString());
    detailPane.setCaretPosition(0);
    updateButtons();
  }

  // ------------------------------------------------------------------ 操作

  private void reply() {
    Discussion selected = list.getSelectedValue();
    if (selected == null) {
      return;
    }
    String body = Messages.showMultilineInputDialog(
        project, "回复 " + selected.firstAuthor() + " 的这条讨论：", "回复讨论",
        "", Messages.getQuestionIcon(), null);
    if (body == null || body.isBlank()) {
      return;
    }
    run("回复讨论", (review, path, mrIid) -> {
      review.reply(path, mrIid, selected.id, body.trim());
      return true;
    }, null);
  }

  private void addComment() {
    String body = Messages.showMultilineInputDialog(
        project, "对 !" + iid + " 发表评论（将创建新的讨论）：", "发表评论",
        "", Messages.getQuestionIcon(), null);
    if (body == null || body.isBlank()) {
      return;
    }
    GitLabReviewService review = review();
    String path = projectPath();
    String mrIid = iid;
    if (review == null || path == null || mrIid == null) {
      return;
    }
    setBusy(true, GitLabPlusBundle.message("status.commenting"));
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        review.addDiscussion(path, mrIid, body.trim());
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, GitLabPlusBundle.message("status.commentPosted"));
          notify("评论已发表", NotificationType.INFORMATION);
          reload();
        });
      }
      catch (Exception t) {
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, GitLabPlusBundle.message("status.commentFailed"));
          showError("发表评论失败", t);
        });
      }
    });
  }

  private void resolve(boolean resolved) {
    Discussion selected = list.getSelectedValue();
    if (selected == null) {
      return;
    }
    run(resolved ? "Resolve" : "Unresolve",
        (review, path, mrIid) -> {
          review.resolve(path, mrIid, selected.id, resolved);
          return true;
        }, null);
  }

  /**
   * 👍 是 toggle：已点过则取消，未点过则添加。
   *
   * <p>GitLab 对同一用户重复添加相同 reaction 会报错（已被占用），
   * 所以必须先判断“我是否已点过”，再决定 POST 还是 DELETE。</p>
   */
  private void thumbsUp() {
    Discussion selected = list.getSelectedValue();
    GitLabReviewService review = review();
    String path = projectPath();
    String mrIid = iid;
    if (selected == null || review == null || path == null || mrIid == null) {
      return;
    }
    Discussion.Note target = awardTargetNote(selected);
    if (target == null) {
      statusLabel.setText("该讨论只有系统消息，没有可点赞的评论");
      return;
    }
    setBusy(true, GitLabPlusBundle.message("status.switchingReaction"));
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        String me = review.currentUsername();
        Discussion.Note.Award mine = null;
        // 必须单独查 reaction：discussions 响应里不含 award_emoji
        for (Discussion.Note.Award award : review.listAwards(path, mrIid, target.id)) {
          if ("thumbsup".equals(award.name()) && award.username().equals(me)) {
            mine = award;
            break;
          }
        }
        if (mine != null) {
          review.removeAward(path, mrIid, target.id, mine.id());
        }
        else {
          review.addAward(path, mrIid, target.id, "thumbsup");
        }
        boolean removed = mine != null;
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, removed ? "已取消 👍" : "已点赞 👍");
          reload();
        });
      }
      catch (Exception t) {
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, GitLabPlusBundle.message("status.reactionFailed"));
          showError("点赞失败", t);
        });
      }
    });
  }

  /** 点赞目标 note（跳过系统消息）。 */
  public static Discussion.Note awardTargetNote(Discussion discussion) {
    if (discussion == null) {
      return null;
    }
    for (Discussion.Note note : discussion.notes) {
      if (!note.system && note.id != null && !note.id.isBlank()) {
        return note;
      }
    }
    return null;
  }

  /** 点赞目标 note id（无可用目标时返回 null）。 */
  public static String awardTargetNoteId(Discussion discussion) {
    Discussion.Note note = awardTargetNote(discussion);
    return note == null ? null : note.id;
  }


  /** 统一操作执行：成功后回调 onSuccess 与 reload。 */
  private void run(String label, ReviewAction action, @Nullable Runnable onSuccess) {
    GitLabReviewService review = review();
    String path = projectPath();
    String mrIid = iid;
    if (review == null || path == null || mrIid == null) {
      return;
    }
    setBusy(true, label + "…");
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        action.execute(review, path, mrIid);
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, label + " 完成");
          if (onSuccess != null) {
            onSuccess.run();
          }
          reload();
        });
      }
      catch (Exception t) {
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, label + " 失败");
          showError(label + " 失败", t);
        });
      }
    });
  }

  @FunctionalInterface
  private interface ReviewAction {
    boolean execute(GitLabReviewService review, String projectPath, String iid);
  }

  // ------------------------------------------------------------------ 状态与工具

  private @Nullable GitLabReviewService review() {
    GitLabService service = serviceSupplier.get();
    if (service == null) {
      return null;
    }
    GitLabClient client = service.client();
    return new GitLabReviewService(client);
  }

  private @Nullable String projectPath() {
    String path = projectPathSupplier.get();
    return path == null || path.isBlank() ? null : path;
  }

  private void setBusy(boolean busy, String status) {
    this.busy = busy;
    if (status != null) {
      statusLabel.setText(status);
    }
    updateButtons();
  }

  private void updateButtons() {
    boolean hasIid = iid != null && !iid.isBlank();
    boolean enabled = hasIid && !busy;
    boolean selected = enabled && list.getSelectedValue() != null;
    reloadButton.setEnabled(enabled);
    resolveButton.setEnabled(selected);
    unresolveButton.setEnabled(selected);
    thumbsUpButton.setEnabled(selected);
    replyButton.setEnabled(selected);
    commentButton.setEnabled(enabled);
    showActivityCheck.setEnabled(enabled);
  }

  private void notify(String content, NotificationType type) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup(NOTIFICATION_GROUP)
        .createNotification("GitLab+", content, type)
        .notify(project);
  }

  private void showError(String title, Throwable t) {
    String message = t instanceof GitLabClient.GitLabApiException ? t.getMessage() : rootMessage(t);
    Messages.showErrorDialog(project, message, title);
  }

  private static String rootMessage(Throwable t) {
    Throwable cur = t;
    while (cur.getCause() != null && cur.getCause() != cur) {
      cur = cur.getCause();
    }
    String message = cur.getMessage();
    return message == null || message.isBlank() ? cur.getClass().getSimpleName() : message;
  }

  private static String oneLine(String text, int limit) {
    if (text == null) {
      return "";
    }
    String flat = text.replace('\n', ' ').replace('\r', ' ').trim();
    return flat.length() <= limit ? flat : flat.substring(0, limit) + "…";
  }

  private static String escape(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
