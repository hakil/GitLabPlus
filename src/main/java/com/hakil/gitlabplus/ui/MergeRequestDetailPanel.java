package com.hakil.gitlabplus.ui;

import com.hakil.gitlabplus.api.GitLabClient;
import com.hakil.gitlabplus.git.GitRefresher;
import com.hakil.gitlabplus.git.PostMergeActions;
import com.hakil.gitlabplus.model.MarkdownRenderer;
import com.hakil.gitlabplus.model.ChangesPage;
import com.hakil.gitlabplus.i18n.GitLabPlusBundle;
import com.hakil.gitlabplus.model.MergeRequestDetails;
import com.hakil.gitlabplus.model.ProjectMember;
import com.hakil.gitlabplus.service.GitLabDiffService;
import com.hakil.gitlabplus.service.GitLabService;
import com.hakil.gitlabplus.settings.GitLabPlusSettings;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * MR 详情面板（P1）：对齐官方 review 详情页的信息面与操作集合。
 *
 * <p>元信息、描述、时间线只读展示；操作包括 Merge / Squash、Approve / Revoke、
 * Close / Reopen、Draft / Ready、Rebase、设置审阅人、打开网页。</p>
 */
public final class MergeRequestDetailPanel {

  private static final String NOTIFICATION_GROUP = "GitLabPlus.Notifications";

  private final Project project;
  private final Supplier<GitLabService> serviceSupplier;
  private final Supplier<String> projectPathSupplier;
  private final Runnable onDataChanged;

  private final JPanel root = new JPanel(new BorderLayout(0, 6));
  private final JLabel titleLabel = new JLabel("未选择 merge request");
  private final JLabel statusLabel = new JLabel(" ");
  private final JTextPane overviewPane = htmlPane();
  private final JTextPane descriptionPane = htmlPane();
  private final ReviewPanel reviewPanel;
  private final DiffPanel diffPanel;
  private final CiPanel ciPanel;

  private final JButton mergeButton = new JButton(GitLabPlusBundle.message("detail.action.merge"));
  private final JButton squashButton = new JButton(GitLabPlusBundle.message("detail.action.squashMerge"));
  private final JButton approveButton = new JButton(GitLabPlusBundle.message("detail.action.approve"));
  private final JButton revokeButton = new JButton(GitLabPlusBundle.message("detail.action.revoke"));
  /** 当前账号是否已批准（来自 REST approvals；canApprove 只表示“能否批准”）。 */
  private boolean userHasApproved;
  private final JButton closeButton = new JButton(GitLabPlusBundle.message("detail.action.close"));
  private final JButton reopenButton = new JButton(GitLabPlusBundle.message("detail.action.reopen"));
  private final JButton draftButton = new JButton(GitLabPlusBundle.message("detail.action.draft"));
  private final JButton rebaseButton = new JButton(GitLabPlusBundle.message("detail.action.rebase"));
  private final JButton reviewersButton = new JButton(GitLabPlusBundle.message("detail.action.reviewers"));
  private final JButton webButton = new JButton(GitLabPlusBundle.message("detail.action.openWeb"));
  private final JButton reloadButton = new JButton(GitLabPlusBundle.message("detail.action.refreshDetails"));
  private final JButton moreButton = new JButton(GitLabPlusBundle.message("action.more") + " \u25BE");

  private @Nullable String iid;
  private @Nullable MergeRequestDetails current;
  private boolean busy;

  public MergeRequestDetailPanel(Project project,
                                 Supplier<GitLabService> serviceSupplier,
                                 Supplier<String> projectPathSupplier,
                                 Runnable onDataChanged) {
    this.project = project;
    this.serviceSupplier = serviceSupplier;
    this.projectPathSupplier = projectPathSupplier;
    this.onDataChanged = onDataChanged;
    this.reviewPanel = new ReviewPanel(project, serviceSupplier, projectPathSupplier);
    this.diffPanel = new DiffPanel(project, serviceSupplier, projectPathSupplier);
    this.ciPanel = new CiPanel(project, serviceSupplier, projectPathSupplier);
    buildUi();
    updateButtons();
  }

  public JComponent component() {
    return root;
  }

  /** 包裹列表与详情，形成官方风格的主从布局。 */
  public JSplitPane wrapWithList(JComponent listComponent) {
    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listComponent, root);
    split.setResizeWeight(0.4);
    split.setContinuousLayout(true);
    split.setOneTouchExpandable(true);
    split.setDividerLocation(0.4);
    split.setMinimumSize(new Dimension(200, 90));
    SplitRatioKeeper.keep(split, 0.4, 40);
    return split;
  }

  private void buildUi() {
    titleLabel.setFont(titleLabel.getFont().deriveFont(titleLabel.getFont().getStyle() | java.awt.Font.BOLD, 14f));
    descriptionPane.setEditable(false);

    JPanel header = new JPanel(new BorderLayout());
    header.add(titleLabel, BorderLayout.NORTH);
    header.add(statusLabel, BorderLayout.SOUTH);

    JPanel infoPanel = new JPanel(new BorderLayout(0, 2));
    infoPanel.add(new JLabel("概览"), BorderLayout.NORTH);
    infoPanel.add(new JBScrollPane(overviewPane), BorderLayout.CENTER);
    infoPanel.setPreferredSize(new Dimension(360, 140));
    infoPanel.setMinimumSize(new Dimension(160, 36));

    JPanel descPanel = new JPanel(new BorderLayout(0, 2));
    descPanel.add(new JLabel("描述"), BorderLayout.NORTH);
    descPanel.add(new JBScrollPane(descriptionPane), BorderLayout.CENTER);
    descPanel.setPreferredSize(new Dimension(360, 80));
    descPanel.setMinimumSize(new Dimension(160, 28));

    // 概览与描述用垂直分割：窗口被压矮时两者按比例一起缩，而不是把描述区压没
    JSplitPane overviewTab = new JSplitPane(JSplitPane.VERTICAL_SPLIT, infoPanel, descPanel);
    overviewTab.setResizeWeight(0.6);
    overviewTab.setContinuousLayout(true);
    overviewTab.setOneTouchExpandable(true);
    overviewTab.setDividerLocation(0.6);
    SplitRatioKeeper.keep(overviewTab, 0.6, 24);

    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab(GitLabPlusBundle.message("detail.overview"), overviewTab);
    tabs.addTab(GitLabPlusBundle.message("detail.discussions"), reviewPanel.component());
    tabs.addTab(GitLabPlusBundle.message("detail.changes"), diffPanel.component());
    tabs.addTab(GitLabPlusBundle.message("detail.ci"), ciPanel.component());

    // 主操作只留 3 个按钮，其余收进「更多」菜单 —— 避免按钮行把窗口宽度顶住无法缩小
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
    actions.add(mergeButton);
    actions.add(squashButton);
    actions.add(approveButton);
    actions.add(moreButton);

    JPanel center = new JPanel(new BorderLayout(0, 4));
    center.add(tabs, BorderLayout.CENTER);

    root.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
    root.add(header, BorderLayout.NORTH);
    root.add(center, BorderLayout.CENTER);
    root.add(actions, BorderLayout.SOUTH);

    mergeButton.addActionListener(e -> merge(false));
    moreButton.addActionListener(e -> showMoreMenu());
    squashButton.addActionListener(e -> merge(true));
    approveButton.addActionListener(e -> run("Approve", (service, path, id) -> service.approve(path, id)));
    revokeButton.addActionListener(e -> run("Revoke approval", (service, path, id) -> service.unapprove(path, id)));
    closeButton.addActionListener(e -> {
      if (confirm("确定关闭 !" + iid + " ？")) {
        run("Close", (service, path, id) -> service.updateState(path, id, "CLOSE"));
      }
    });
    reopenButton.addActionListener(e -> run("Reopen", (service, path, id) -> service.updateState(path, id, "REOPEN")));
    draftButton.addActionListener(e -> toggleDraft());
    rebaseButton.addActionListener(e -> {
      if (confirm("对 !" + iid + " 的源分支执行 rebase？")) {
        run("Rebase", (service, path, id) -> service.rebase(path, id));
      }
    });
    reviewersButton.addActionListener(e -> editReviewers());
    webButton.addActionListener(e -> openWeb());
    reloadButton.addActionListener(e -> load(iid));
  }

  // ------------------------------------------------------------------ 加载与渲染

  /** 加载指定 MR 的详情与时间线；iid 为 null 时清空。 */
  /**
   * Approve 可用：能批准且尚未批准。
   *
   * <p>抽成纯函数便于单测：GitLab 在「我已批准」后会返回 canApprove=false，
   * 所以两个按钮不能都用 canApprove 判断。</p>
   */
  public static boolean approveEnabled(boolean canApprove, boolean userHasApproved) {
    return canApprove && !userHasApproved;
  }

  /** Revoke 可用：仅当我已经批准过。 */
  public static boolean revokeEnabled(boolean userHasApproved) {
    return userHasApproved;
  }

  public void load(@Nullable String iid) {
    this.iid = iid;
    if (iid == null || iid.isBlank()) {
      current = null;
      titleLabel.setText("未选择 merge request");
      statusLabel.setText(" ");
      overviewPane.setText("");
      descriptionPane.setText("");
      reviewPanel.load(null);
      updateButtons();
      return;
    }
    GitLabService service = serviceSupplier.get();
    if (service == null) {
      return;
    }
    String path = projectPathSupplier.get();
    if (path == null || path.isBlank()) {
      return;
    }
    setBusy(true, "加载详情…");
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        MergeRequestDetails details = service.loadFullDetails(path, iid);
        // 区分「我能批准」(canApprove) 与「我已批准」(user_has_approved)：
        // Revoke 的可用性只看后者。
        boolean approved;
        try {
          approved = service.userHasApproved(path, iid);
        }
        catch (Throwable ignored) {
          approved = false;
        }
        boolean approvedFlag = approved;
        UIUtil.invokeLaterIfNeeded(() -> {
          userHasApproved = approvedFlag;
          current = details;
          render(details);
          reviewPanel.load(details.iid);
          diffPanel.load(details.iid);
          ciPanel.load(details.iid);
          setBusy(false, "就绪");
        });
      }
      catch (Exception t) {
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, "详情加载失败");
          showError("加载详情失败", t);
        });
      }
    });
  }

  private void render(MergeRequestDetails details) {
    titleLabel.setText("!" + details.iid + "  " + details.title);
    statusLabel.setText(details.statusSummary());
    overviewPane.setText(buildOverviewHtml(details));
    overviewPane.setCaretPosition(0);
    descriptionPane.setText(MarkdownRenderer.toHtml(details.description));
    descriptionPane.setCaretPosition(0);
    updateButtons();
  }

  private String buildOverviewHtml(MergeRequestDetails d) {
    StringBuilder sb = new StringBuilder("<html><body style='font-family:sans-serif;font-size:11px'>");
    row(sb, "状态", d.state);
    row(sb, "分支", d.displayBranch());
    row(sb, "作者", d.author);
    row(sb, "指派", join(d.assignees));
    row(sb, "审阅人", join(d.reviewers));
    row(sb, "已批准", join(d.approvedBy));
    row(sb, "标签", join(d.labels));
    row(sb, "CI", d.pipelineStatus);
    row(sb, "可合并", (d.mergeable ? "是" : "否") + (d.conflicts ? "（有冲突）" : ""));
    row(sb, "Draft", d.draft ? "是" : "否");
    row(sb, "Squash", (d.squashOnMerge ? "默认开启" : "默认关闭") + (d.squashReadOnly ? "（项目锁定）" : ""));
    row(sb, "创建", d.createdAt);
    row(sb, "更新", d.updatedAt);
    row(sb, "权限", "merge=" + d.canMerge + " approve=" + d.canApprove);
    sb.append("</body></html>");
    return sb.toString();
  }

  private static void row(StringBuilder sb, String key, String value) {
    sb.append("<div><b>").append(escape(key)).append("</b>：")
        .append(escape(value == null || value.isBlank() ? "—" : value)).append("</div>");
  }


  // ------------------------------------------------------------------ 操作

  private void merge(boolean squash) {
    MergeRequestDetails details = current;
    if (details == null) {
      return;
    }
    GitLabService service = serviceSupplier.get();
    String path = projectPathSupplier.get();
    if (service == null || path == null || path.isBlank()) {
      return;
    }
    String blockReason = validateMergeable(details, squash);
    if (blockReason != null) {
      Messages.showWarningDialog(project, blockReason, "GitLab+ 无法合并");
      return;
    }
    MergeDialog dialog = new MergeDialog(project, details, squash);
    if (!dialog.showAndGet()) {
      return;
    }
    setBusy(true, "合并中…");
    boolean useSquash = dialog.squash();
    String commitMessage = dialog.commitMessage();
    String squashMessage = dialog.squashCommitMessage();
    boolean deleteSource = dialog.deleteSourceBranch();
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        // head sha 不从 GraphQL 取：DiffRefs.headSha 是非空字段，
        // 对无 diff 的 MR 会返回 null 并拖垓整个查询（Cannot return null for non-nullable field）。
        // 合并前改用 REST /changes 的 diff_refs（与行内评论同一数据源）。
        String headSha = resolveHeadSha(service, path, details);
        service.accept(path, details.iid, headSha, commitMessage, squashMessage, useSquash, deleteSource);
        String state = service.awaitMerged(path, details.iid);
        boolean merged = "MERGED".equalsIgnoreCase(state);
        GitRefresher.Result fetch = GitRefresher.fetchAll(project);
        GitLabPlusSettings settings = GitLabPlusSettings.getInstance();
        PostMergeActions.Result post = PostMergeActions.apply(
            project, details.targetBranch, details.sourceBranch,
            settings.isCheckoutTargetAfterMerge(), settings.isDeleteLocalSourceAfterMerge());
        String message = merged
            ? "!<b>" + details.iid + "</b> 已合并。" + fetch.message() + post.summary()
            : "合并请求已提交，当前状态 <b>" + state + "</b>。" + fetch.message() + post.summary();
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, merged ? "已合并" : "状态：" + state);
          notify(message, merged ? NotificationType.INFORMATION : NotificationType.WARNING);
          onDataChanged.run();
          load(details.iid);
        });
      }
      catch (Exception t) {
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, "合并失败");
          showError("合并失败", t);
        });
      }
    });
  }

  /** 优先用已解析到的 sha，否则从 REST changes 取 diff_refs.head_sha。 */
  private static String resolveHeadSha(GitLabService service, String path, MergeRequestDetails details) {
    if (details.headSha != null && !details.headSha.isBlank()) {
      return details.headSha;
    }
    ChangesPage page = new GitLabDiffService(service.client()).loadChanges(path, details.iid);
    String sha = page.diffRefs().headSha();
    if (sha == null || sha.isBlank()) {
      throw new GitLabClient.GitLabApiException(
          "无法获取 head sha（该 MR 可能没有代码变更），请在 GitLab 网页确认后再合并");
    }
    return sha;
  }

  /** 「更多」菜单：低频操作集中在这里，保持主界面窄。 */
  private void showMoreMenu() {
    JPopupMenu menu = new JPopupMenu();
    addMenuItem(menu, GitLabPlusBundle.message("detail.action.revoke"), () -> run("Revoke approval",
        (service, path, id) -> service.unapprove(path, id)), revokeButton.isEnabled());
    menu.addSeparator();
    addMenuItem(menu, GitLabPlusBundle.message("detail.action.close"), () -> {
      if (confirm("确定关闭 !" + iid + " ？")) {
        run("Close", (service, path, id) -> service.updateState(path, id, "CLOSE"));
      }
    }, closeButton.isEnabled());
    addMenuItem(menu, GitLabPlusBundle.message("detail.action.reopen"), () -> run("Reopen",
        (service, path, id) -> service.updateState(path, id, "REOPEN")), reopenButton.isEnabled());
    addMenuItem(menu, draftButton.getText(), this::toggleDraft, draftButton.isEnabled());
    menu.addSeparator();
    addMenuItem(menu, GitLabPlusBundle.message("detail.action.rebase"), () -> {
      if (confirm("对 !" + iid + " 的源分支执行 rebase？")) {
        run("Rebase", (service, path, id) -> service.rebase(path, id));
      }
    }, rebaseButton.isEnabled());
    addMenuItem(menu, GitLabPlusBundle.message("detail.action.reviewers") + "...", this::editReviewers, reviewersButton.isEnabled());
    menu.addSeparator();
    addMenuItem(menu, GitLabPlusBundle.message("detail.action.openWeb"), this::openWeb, webButton.isEnabled());
    addMenuItem(menu, GitLabPlusBundle.message("detail.action.refreshDetails"), () -> load(iid), reloadButton.isEnabled());
    menu.show(moreButton, 0, moreButton.getHeight());
  }

  private static void addMenuItem(JPopupMenu menu, String text, Runnable action, boolean enabled) {
    JMenuItem item = new JMenuItem(text);
    item.setEnabled(enabled);
    item.addActionListener(e -> action.run());
    menu.add(item);
  }

  private @Nullable String validateMergeable(MergeRequestDetails details, boolean squash) {
    if (!details.isOpen()) {
      return "该 merge request 当前状态为 " + details.state + "，无法合并。";
    }
    if (!details.canMerge) {
      return "当前 token 所属账号没有该 MR 的合并权限。";
    }
    if (details.draft) {
      return "该 merge request 仍是 Draft，需先标记为 Ready 才能合并。";
    }
    if (details.conflicts) {
      return "该 merge request 存在冲突，无法合并。";
    }
    if (!details.mergeable) {
      return "GitLab 报告该 merge request 当前不可合并（mergeable=false）。";
    }
    if (squash && details.squashReadOnly && !details.squashOnMerge) {
      return "该项目的 squash 设置不允许在合并时更改，无法执行 squash and merge。";
    }
    return null;
  }

  private void toggleDraft() {
    MergeRequestDetails details = current;
    if (details == null) {
      return;
    }
    run(details.draft ? "标记 Ready" : "设为 Draft",
        (service, path, id) -> service.setDraft(path, id, !details.draft));
  }

  /** 从项目成员里选审阅人（不再让用户手输用户名）。 */
  private void editReviewers() {
    MergeRequestDetails details = current;
    GitLabService service = serviceSupplier.get();
    String path = projectPathSupplier.get();
    if (details == null || service == null || path == null || path.isBlank()) {
      return;
    }
    setBusy(true, "加载项目成员…");
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        List<ProjectMember> members = service.listProjectMembers(path);
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, "就绪");
          if (members.isEmpty()) {
            Messages.showWarningDialog("没有取到项目成员（可能是权限或项目路径不对）", "设置审阅人");
            return;
          }
          ReviewerPickerDialog dialog =
              new ReviewerPickerDialog(project, members, details.reviewers);
          if (!dialog.showAndGet()) {
            return;
          }
          List<String> usernames = dialog.selectedUsernames();
          run("设置审阅人", (svc, p, id) -> svc.setReviewers(p, id, usernames));
        });
      }
      catch (Exception t) {
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, "加载成员失败");
          Messages.showErrorDialog("加载项目成员失败：" + t.getMessage(), "设置审阅人");
        });
      }
    });
  }

  private void openWeb() {
    MergeRequestDetails details = current;
    if (details == null || details.webUrl == null || details.webUrl.isBlank()) {
      return;
    }
    com.intellij.ide.BrowserUtil.browse(details.webUrl);
  }

  private void run(String label, Action action) {
    MergeRequestDetails details = current;
    if (details == null) {
      return;
    }
    GitLabService service = serviceSupplier.get();
    String path = projectPathSupplier.get();
    if (service == null || path == null || path.isBlank()) {
      return;
    }
    setBusy(true, label + "…");
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        action.execute(service, path, details.iid);
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, label + " 完成");
          notify("!" + details.iid + " " + label + " 成功", NotificationType.INFORMATION);
          onDataChanged.run();
          load(details.iid);
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

  /** 三个参数的操作（便于 lambda 传 service / projectPath / iid）。 */
  @FunctionalInterface
  private interface Action {
    void execute(GitLabService service, String projectPath, String iid);
  }

  // ------------------------------------------------------------------ 状态

  private void updateButtons() {
    MergeRequestDetails d = current;
    boolean has = d != null && !busy;
    boolean open = has && d.isOpen();
    mergeButton.setEnabled(open && d.canMerge);
    squashButton.setEnabled(open && d.canMerge);
    approveButton.setEnabled(has && open && approveEnabled(d.canApprove, userHasApproved));
    // Revoke 的可用性取决于「我已经批准了」，而不是 canApprove：
    // 已批准后 GitLab 会把 canApprove 置 false，用它判断会把撤销按钮永久置灰。
    revokeButton.setEnabled(has && open && revokeEnabled(userHasApproved));
    closeButton.setEnabled(open && d.canUpdate);
    reopenButton.setEnabled(has && !d.isOpen() && !d.isMerged() && d.canUpdate);
    draftButton.setEnabled(open && d.canUpdate);
    draftButton.setText(has && d.draft ? "标记 Ready" : "设为 Draft");
    rebaseButton.setEnabled(has && open);
    reviewersButton.setEnabled(has && d.canUpdate);
    webButton.setEnabled(has && d.webUrl != null && !d.webUrl.isBlank());
    reloadButton.setEnabled(has);
  }

  private void setBusy(boolean busy, String status) {
    this.busy = busy;
    if (status != null) {
      statusLabel.setText(status);
    }
    updateButtons();
  }

  private void notify(String html, NotificationType type) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup(NOTIFICATION_GROUP)
        .createNotification("GitLab+", html, type)
        .notify(project);
  }

  private void showError(String title, Throwable t) {
    String message = t instanceof GitLabClient.GitLabApiException ? t.getMessage() : rootMessage(t);
    Messages.showErrorDialog(project, message, title);
  }

  private boolean confirm(String message) {
    return Messages.showYesNoDialog(project, message, "GitLab+", Messages.getQuestionIcon()) == Messages.YES;
  }

  private static String rootMessage(Throwable t) {
    Throwable cur = t;
    while (cur.getCause() != null && cur.getCause() != cur) {
      cur = cur.getCause();
    }
    String message = cur.getMessage();
    return message == null || message.isBlank() ? cur.getClass().getSimpleName() : message;
  }

  private static String join(List<String> values) {
    return values == null || values.isEmpty() ? "—" : String.join(", ", values);
  }

  private static String escape(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static JTextPane htmlPane() {
    JTextPane pane = new JTextPane();
    pane.setContentType("text/html");
    pane.setEditable(false);
    pane.setBackground(UIUtil.getPanelBackground());
    return pane;
  }
}
