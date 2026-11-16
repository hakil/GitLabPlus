package com.caidailian.gitlabplus.ui;

import com.caidailian.gitlabplus.api.GitLabClient;
import com.caidailian.gitlabplus.i18n.GitLabPlusBundle;
import com.caidailian.gitlabplus.model.ChangesPage;
import com.caidailian.gitlabplus.model.DiffParser;
import com.caidailian.gitlabplus.model.SuggestionBuilder;
import com.caidailian.gitlabplus.service.GitLabDiffService;
import com.caidailian.gitlabplus.service.GitLabService;
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
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Diff 评审面板（P3）：变更文件列表 + 逐文件 unified diff + 行内评论。
 *
 * <p>行号与 position 参数来自 {@link DiffParser}（本地解析），提交走
 * {@link GitLabDiffService#addDiffNote}（POST + query 形式的 position）。</p>
 */
public final class DiffPanel {

  private static final String NOTIFICATION_GROUP = "GitLabPlus.Notifications";

  private final Project project;
  private final Supplier<GitLabService> serviceSupplier;
  private final Supplier<String> projectPathSupplier;

  private final JPanel root = new JPanel(new BorderLayout(0, 4));
  private final DefaultListModel<FileItem> fileModel = new DefaultListModel<>();
  private final JList<FileItem> fileList = new JList<>(fileModel);
  private final DefaultListModel<LineItem> lineModel = new DefaultListModel<>();
  private final JList<LineItem> lineList = new JList<>(lineModel);
  private final JButton reloadButton = new JButton(GitLabPlusBundle.message("diff.load"));
  private final JButton commentButton = new JButton(GitLabPlusBundle.message("diff.commentLine"));
  private final JButton suggestionButton = new JButton(GitLabPlusBundle.message("diff.suggest"));
  private final JButton viewedButton = new JButton(GitLabPlusBundle.message("diff.markViewed"));
  private final JButton ideDiffButton = new JButton(GitLabPlusBundle.message("diff.openInIde"));
  private final JCheckBox hideViewedCheck = new JCheckBox(GitLabPlusBundle.message("diff.hideViewed"));
  private final JLabel statusLabel = new JLabel(" ");

  private @Nullable String iid;
  private @Nullable ChangesPage page;
  private boolean busy;

  public DiffPanel(Project project,
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
    Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    lineList.setCellRenderer(new LineRenderer(mono));
    lineList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    fileList.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                    boolean selected, boolean focused) {
        boolean viewed = value instanceof FileItem item && isViewed(item.file);
        String text = value instanceof FileItem item
            ? (viewed ? "✓ " : "    ") + item.file.displayPath()
                + "   (+" + item.file.addedLines() + "/-" + item.file.removedLines() + ")"
            : String.valueOf(value);
        Component component = super.getListCellRendererComponent(list, text, index, selected, focused);
        // 已读文件置灰：之前只写不读，现在才有真正的视觉反馈
        if (!selected && viewed) {
          component.setForeground(java.awt.Color.GRAY);
        }
        return component;
      }
    });
    fileList.addListSelectionListener(e -> showSelectedFile());

    JPanel filePane = new JPanel(new BorderLayout(0, 2));
    filePane.add(new JLabel(GitLabPlusBundle.message("diff.label.files")), BorderLayout.NORTH);
    filePane.add(new JBScrollPane(fileList), BorderLayout.CENTER);
    filePane.setPreferredSize(new Dimension(240, 200));
    filePane.setMinimumSize(new Dimension(120, 60));

    JPanel linePane = new JPanel(new BorderLayout(0, 2));
    linePane.add(new JLabel(GitLabPlusBundle.message("diff.label.diff")), BorderLayout.NORTH);
    linePane.add(new JBScrollPane(lineList), BorderLayout.CENTER);

    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, filePane, linePane);
    split.setResizeWeight(0.28);
    split.setDividerLocation(0.28);
    SplitRatioKeeper.keep(split, 0.28, 30);

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
    actions.add(reloadButton);
    actions.add(commentButton);
    actions.add(suggestionButton);
    actions.add(viewedButton);
    actions.add(ideDiffButton);
    actions.add(hideViewedCheck);
    actions.add(statusLabel);

    root.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    root.add(split, BorderLayout.CENTER);
    root.add(actions, BorderLayout.SOUTH);

    reloadButton.addActionListener(e -> load(iid));
    commentButton.addActionListener(e -> commentSelectedLine());
    suggestionButton.addActionListener(e -> suggestSelectedLine());
    viewedButton.addActionListener(e -> toggleViewed());
    hideViewedCheck.addActionListener(e -> rebuildFileList());
    ideDiffButton.addActionListener(e -> openInIdeDiff());
    // 选中行变化会影响「评论选中行 / 建议修改」的可用性
    lineList.addListSelectionListener(e -> updateButtons());
    updateButtons();
  }

  // ------------------------------------------------------------------ 数据

  public void load(@Nullable String iid) {
    this.iid = iid;
    page = null;
    fileModel.clear();
    lineModel.clear();
    updateButtons();
    if (iid == null || iid.isBlank()) {
      statusLabel.setText(" ");
      return;
    }
    GitLabDiffService diffService = diffService();
    String path = projectPath();
    if (diffService == null || path == null) {
      return;
    }
    setBusy(true, GitLabPlusBundle.message("status.loadingChanges"));
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        ChangesPage loaded = diffService.loadChanges(path, iid);
        UIUtil.invokeLaterIfNeeded(() -> {
          page = loaded;
          // headSha 变化后清掉失效的已读记录，避免配置无限增长
          if (loaded.diffRefs().complete()) {
            java.util.List<String> filePaths = new java.util.ArrayList<>();
            for (DiffParser.FileDiff file : loaded.files()) {
              filePaths.add(file.displayPath());
            }
            com.caidailian.gitlabplus.storage.ViewedNotesStore.clearStale(
                path, iid, loaded.diffRefs().headSha(), filePaths);
          }
          rebuildFileList();
          setBusy(false, loaded.files().size() + " 个文件，+"
              + loaded.totalAdded() + "/-" + loaded.totalRemoved()
              + (loaded.diffRefs().complete() ? "" : "（diff_refs 缺失，行内评论不可用）"));
          if (!fileModel.isEmpty()) {
            fileList.setSelectedIndex(0);
          }
          updateButtons();
        });
      }
      catch (Exception t) {
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, GitLabPlusBundle.message("status.loadChangesFailed") + "：" + message(t));
          showError("加载变更失败", t);
        });
      }
    });
  }

  /** 单文件最多渲染的行数：超大 diff 全量插进 JList 会让 UI 卡顿。 */
  private static final int MAX_LINES_PER_FILE = 3000;

  private void showSelectedFile() {
    lineModel.clear();
    FileItem item = fileList.getSelectedValue();
    if (item == null) {
      updateButtons();
      return;
    }
    if (item.file.hunks().isEmpty()) {
      lineModel.addElement(LineItem.info("（该文件没有文本 diff，可能是二进制或仅重命名）"));
      updateButtons();
      return;
    }
    int added = 0;
    boolean truncated = false;
    for (DiffParser.Hunk hunk : item.file.hunks()) {
      if (added >= MAX_LINES_PER_FILE) {
        truncated = true;
        break;
      }
      lineModel.addElement(LineItem.header(hunk));
      added++;
      for (int i = 0; i < hunk.lines().size(); i++) {
        if (added >= MAX_LINES_PER_FILE) {
          truncated = true;
          break;
        }
        lineModel.addElement(LineItem.of(hunk.lines().get(i), hunk, i));
        added++;
      }
      if (truncated) {
        break;
      }
    }
    if (truncated) {
      lineModel.addElement(LineItem.info(
          "（该文件 diff 超过 " + MAX_LINES_PER_FILE + " 行，仅显示前 " + MAX_LINES_PER_FILE
              + " 行；完整内容请在网页查看）"));
    }
    updateButtons();
  }

  // ------------------------------------------------------------------ 行内评论

  private void commentSelectedLine() {
    LineItem item = lineList.getSelectedValue();
    FileItem fileItem = fileList.getSelectedValue();
    if (item == null || item.diffLine == null || fileItem == null) {
      return;
    }
    String body = Messages.showMultilineInputDialog(project,
                                                    "对 " + fileItem.file.displayPath() + " 第 "
                                                        + displayLine(item.diffLine) + " 行的评论：",
                                                    "GitLab+ 行内评论",
                                                    "",
                                                    Messages.getQuestionIcon(),
                                                    null);
    if (body == null || body.isBlank()) {
      return;
    }
    postInlineComment(fileItem, item, body.trim(), "发表行内评论…", "行内评论已发表（可在「讨论」页查看）");
  }

  /**
   * 生成 GitLab suggestion 块并发为行内评论。
   *
   * <p>GitLab 没有独立的 suggestion API —— suggestion 只是 note body 中的
   * <code>```suggestion:-N+M</code> 代码块，因此这里先生成块文本，再走同一条行内评论通道。</p>
   */
  private void suggestSelectedLine() {
    LineItem item = lineList.getSelectedValue();
    FileItem fileItem = fileList.getSelectedValue();
    if (item == null || item.diffLine == null || item.hunk == null || fileItem == null) {
      return;
    }
    String replacement = Messages.showMultilineInputDialog(project,
                                                          "建议替换后的内容（会生成 suggestion 块）：",
                                                          "GitLab+ 建议修改",
                                                          item.diffLine.text(),
                                                          Messages.getQuestionIcon(),
                                                          null);
    if (replacement == null) {
      return;
    }
    int[] context = SuggestionBuilder.defaultContext(item.hunk.lines(), item.lineIndexInHunk);
    String body = SuggestionBuilder.build(replacement, context[0], context[1]);
    postInlineComment(fileItem, item, body, "提交建议修改…", "建议修改已提交（对方可在网页上 Apply suggestion）");
  }

  /** 行内评论 / 建议修改的公共提交路径。 */
  private void postInlineComment(FileItem fileItem, LineItem item, String body,
                                 String busyText, String doneText) {
    ChangesPage current = page;
    GitLabDiffService diffService = diffService();
    String path = projectPath();
    if (current == null || diffService == null || path == null || iid == null) {
      return;
    }
    if (!current.diffRefs().complete()) {
      Messages.showWarningDialog(project, "该 MR 未返回完整 diff_refs，无法定位行内评论位置。", "GitLab+");
      return;
    }
    setBusy(true, busyText);
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        diffService.addDiffNote(path, iid, body, current.diffRefs(), fileItem.file, item.diffLine);
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, doneText);
          NotificationGroupManager.getInstance()
              .getNotificationGroup(NOTIFICATION_GROUP)
              .createNotification("GitLab+", doneText, NotificationType.INFORMATION)
              .notify(project);
        });
      }
      catch (Exception t) {
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, GitLabPlusBundle.message("status.inlineCommentFailed"));
          showError("发表行内评论失败", t);
        });
      }
    });
  }

  /**
   * 重建文件列表（应用「隐藏已读」过滤），并尽量保持原来选中的文件。
   */
  private void rebuildFileList() {
    if (page == null) {
      fileModel.clear();
      return;
    }
    String previousPath = fileList.getSelectedValue() == null
        ? null : fileList.getSelectedValue().file.displayPath();
    fileModel.clear();
    for (DiffParser.FileDiff file : page.files()) {
      if (hideViewedCheck.isSelected() && isViewed(file)) {
        continue;
      }
      fileModel.addElement(new FileItem(file));
    }
    if (previousPath != null) {
      for (int i = 0; i < fileModel.size(); i++) {
        if (previousPath.equals(fileModel.get(i).file.displayPath())) {
          fileList.setSelectedIndex(i);
          return;
        }
      }
    }
    if (!fileModel.isEmpty()) {
      fileList.setSelectedIndex(0);
    }
    else {
      lineModel.clear();
    }
  }

  /** 当前文件是否已读（以 headSha 为准，新提交后自动失效）。 */
  private boolean isViewed(DiffParser.FileDiff file) {
    ChangesPage current = page;
    String path = projectPath();
    if (current == null || path == null || iid == null || file == null) {
      return false;
    }
    return com.caidailian.gitlabplus.storage.ViewedNotesStore.isViewed(
        path, iid, file.displayPath(), current.diffRefs().headSha());
  }

  /** 切换选中文件的已读状态（可反复切换，不再是一次性标记）。 */
  private void toggleViewed() {
    FileItem item = fileList.getSelectedValue();
    ChangesPage current = page;
    String path = projectPath();
    if (item == null || current == null || path == null || iid == null) {
      return;
    }
    boolean nowViewed = !isViewed(item.file);
    com.caidailian.gitlabplus.storage.ViewedNotesStore.setViewed(
        path, iid, item.file.displayPath(), current.diffRefs().headSha(), nowViewed);
    fileList.repaint();
    statusLabel.setText((nowViewed ? "已标记已读：" : "已取消已读：") + item.file.displayPath());
    if (hideViewedCheck.isSelected()) {
      rebuildFileList();
    }
    updateButtons();
  }

  // ------------------------------------------------------------------ 状态

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
    reloadButton.setEnabled(enabled);
    FileItem fileItem = fileList.getSelectedValue();
    LineItem lineItem = lineList.getSelectedValue();
    boolean refsOk = page != null && page.diffRefs() != null && page.diffRefs().complete();
    boolean lineSelected = enabled && fileItem != null && lineItem != null
        && lineItem.diffLine != null && refsOk;
    commentButton.setEnabled(lineSelected);
    suggestionButton.setEnabled(lineSelected);
    viewedButton.setEnabled(enabled && fileItem != null);
    ideDiffButton.setEnabled(enabled && fileItem != null);
    viewedButton.setText(fileItem != null && isViewed(fileItem.file)
                              ? GitLabPlusBundle.message("diff.unmarkViewed")
                              : GitLabPlusBundle.message("diff.markViewed"));

    // 把「为什么不可用」说清楚：之前只是静静置灰，用户无法判断是没选中还是 diff_refs 缺失
    String hint;
    if (fileItem == null) {
      hint = null;
    }
    else if (!refsOk) {
      hint = "该 MR 的 diff_refs 不完整，行内评论与建议都不可用（可在网页评论）";
    }
    else if (lineItem == null) {
      hint = "请先在右侧选中一行 diff（hunk 标题行不可评论）";
    }
    else if (lineItem.diffLine == null) {
      hint = "当前选中的是分组标题，请选择具体的 diff 行";
    }
    else {
      hint = null;
    }
    commentButton.setToolTipText(hint == null ? "对选中行发起行内评论" : hint);
    suggestionButton.setToolTipText(hint == null ? "对选中行发起建议修改" : hint);
    if (hint != null && fileItem != null) {
      statusLabel.setText(hint);
    }
  }

  /** 用 IDEA 原生 diff 展示该文件的两侧全文（需要向 GitLab 取 base/head 原始内容）。 */
  private void openInIdeDiff() {
    FileItem item = fileList.getSelectedValue();
    ChangesPage current = page;
    String path = projectPath();
    GitLabService service = serviceSupplier.get();
    if (item == null || current == null || path == null || service == null) {
      return;
    }
    ChangesPage.DiffRefs refs = current.diffRefs();
    String baseSha = refs == null ? "" : refs.baseSha();
    String headSha = refs == null ? "" : refs.headSha();
    DiffParser.FileDiff file = item.file;
    setBusy(true, GitLabPlusBundle.message("status.fetchingFile"));
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        GitLabDiffService diffService = new GitLabDiffService(service.client());
        String oldText = file.newFile() ? "" : diffService.rawFile(path, file.oldPath(), baseSha);
        String newText = file.deletedFile() ? "" : diffService.rawFile(path, file.newPath(), headSha);
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, GitLabPlusBundle.message("status.ideDiffOpened"));
          showIdeDiff(file.displayPath(), oldText, newText);
        });
      }
      catch (Exception t) {
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, GitLabPlusBundle.message("status.ideDiffFailed"));
          statusLabel.setText("打开 IDEA Diff 失败：" + t.getMessage());
        });
      }
    });
  }

  private void showIdeDiff(String title, String oldText, String newText) {
    com.intellij.diff.DiffContentFactory factory = com.intellij.diff.DiffContentFactory.getInstance();
    com.intellij.diff.contents.DiffContent left = factory.create(project, oldText == null ? "" : oldText);
    com.intellij.diff.contents.DiffContent right = factory.create(project, newText == null ? "" : newText);
    com.intellij.diff.requests.SimpleDiffRequest request = new com.intellij.diff.requests.SimpleDiffRequest(
        title, left, right, "base", "head");
    com.intellij.diff.DiffManager.getInstance().showDiff(project, request);
  }

  private @Nullable GitLabDiffService diffService() {
    GitLabService service = serviceSupplier.get();
    if (service == null) {
      return null;
    }
    GitLabClient client = service.client();
    return new GitLabDiffService(client);
  }

  private @Nullable String projectPath() {
    String path = projectPathSupplier.get();
    return path == null || path.isBlank() ? null : path;
  }

  private void showError(String title, Throwable t) {
    String message = t instanceof GitLabClient.GitLabApiException ? t.getMessage() : message(t);
    Messages.showErrorDialog(project, message, title);
  }

  private static String message(Throwable t) {
    Throwable cur = t;
    while (cur.getCause() != null && cur.getCause() != cur) {
      cur = cur.getCause();
    }
    String message = cur.getMessage();
    return message == null || message.isBlank() ? cur.getClass().getSimpleName() : message;
  }

  private static String displayLine(DiffParser.DiffLine line) {
    if (line.newLine() > 0 && line.oldLine() > 0) {
      return String.valueOf(line.newLine());
    }
    return line.newLine() > 0 ? String.valueOf(line.newLine()) : String.valueOf(line.oldLine());
  }

  // ------------------------------------------------------------------ 列表模型

  private record FileItem(DiffParser.FileDiff file) {
    @Override
    public String toString() {
      return file.displayPath();
    }
  }

  private static final class LineItem {
    private final DiffParser.DiffLine diffLine;
    private final DiffParser.Hunk hunk;
    private final int lineIndexInHunk;
    private final String text;
    private final Color background;

    private LineItem(DiffParser.DiffLine diffLine, DiffParser.Hunk hunk, int lineIndexInHunk,
                     String text, Color background) {
      this.diffLine = diffLine;
      this.hunk = hunk;
      this.lineIndexInHunk = lineIndexInHunk;
      this.text = text;
      this.background = background;
    }

    static LineItem of(DiffParser.DiffLine line, DiffParser.Hunk hunk, int lineIndexInHunk) {
      String oldNo = line.oldLine() > 0 ? String.valueOf(line.oldLine()) : "";
      String newNo = line.newLine() > 0 ? String.valueOf(line.newLine()) : "";
      String body = String.format("%5s %5s  %s%s", oldNo, newNo, line.kind().prefix(), line.text());
      Color bg = switch (line.kind()) {
        case ADDED -> new Color(232, 251, 232);
        case REMOVED -> new Color(253, 232, 232);
        default -> UIUtil.getPanelBackground();
      };
      return new LineItem(line, hunk, lineIndexInHunk, body, bg);
    }

    static LineItem header(DiffParser.Hunk hunk) {
      String text = "@@ -" + hunk.oldStart() + "," + hunk.oldCount()
          + " +" + hunk.newStart() + "," + hunk.newCount() + " @@ " + hunk.header();
      return new LineItem(null, hunk, -1, text, new Color(240, 240, 245));
    }

    static LineItem info(String text) {
      return new LineItem(null, null, -1, text, UIUtil.getPanelBackground());
    }

    @Override
    public String toString() {
      return text;
    }
  }

  private static final class LineRenderer extends DefaultListCellRenderer {
    private final Font mono;

    private LineRenderer(Font mono) {
      this.mono = mono;
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                  boolean selected, boolean focused) {
      JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
      label.setFont(mono);
      if (value instanceof LineItem item) {
        label.setText(item.text);
        if (!selected) {
          label.setBackground(item.background);
        }
        label.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
      }
      return label;
    }
  }
}
