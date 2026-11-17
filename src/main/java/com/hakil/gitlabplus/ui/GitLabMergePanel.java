package com.hakil.gitlabplus.ui;

import com.hakil.gitlabplus.api.GitLabClient;
import com.hakil.gitlabplus.git.BranchLister;
import com.hakil.gitlabplus.git.BranchPusher;
import com.hakil.gitlabplus.git.GitProjectResolver;
import com.hakil.gitlabplus.i18n.GitLabPlusBundle;
import com.hakil.gitlabplus.model.ProjectSelection;
import com.hakil.gitlabplus.git.GitRefresher;
import com.hakil.gitlabplus.model.MergeRequestFilter;
import com.hakil.gitlabplus.model.MergeRequestInfo;
import com.hakil.gitlabplus.model.TemplateRenderer;
import com.hakil.gitlabplus.service.GitLabService;
import com.hakil.gitlabplus.service.SelfCheckService;
import com.hakil.gitlabplus.settings.GitLabPlusSettings;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.util.PropertiesComponent;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * 工具窗口内容：项目 open merge request 列表 + 合并动作。
 *
 * <p>合并成功后自动执行 {@code git fetch --all --prune}（{@link GitRefresher}），
 * 这是内置 GitLab 插件缺失的一环。</p>
 */
public final class GitLabMergePanel implements com.intellij.openapi.Disposable {

  private static final String NOTIFICATION_GROUP = "GitLabPlus.Notifications";
  private static final String KEY_TARGET_BRANCH = "gitlabplus.lastTargetBranch";

  private final Project project;
  private final JPanel root = new JPanel(new BorderLayout(0, 6));
  private final javax.swing.JLabel serverLabel = new javax.swing.JLabel(" ");
  private String lastLoadedPath = "";
  /** 重建下拉项期间抑制 action（removeAllItems/addItem 会触发 combo 事件，否则会递归刷新）。 */
  private boolean refreshingChoices;
  /** 构造期间不触发自动刷新。 */
  private boolean initializing = true;
  private final JComboBox<String> projectCombo = new JComboBox<>();
  private final java.util.Map<String, String> projectServerMap = new java.util.LinkedHashMap<>();
  private final JButton refreshButton = new JButton(GitLabPlusBundle.message("action.refresh"));
  private final JButton selfCheckButton = new JButton(GitLabPlusBundle.message("list.selfCheck"));
  private final JButton createButton = new JButton("+ " + GitLabPlusBundle.message("list.newMr"));
  private final JButton loadMoreButton = new JButton(GitLabPlusBundle.message("list.loadMore"));
  private final JComboBox<String> stateCombo =
      new JComboBox<>(MergeRequestFilter.STATES.toArray(new String[0]));
  private final JTextField searchField = new JTextField(16);
  private final JLabel statusLabel = new JLabel(GitLabPlusBundle.message("list.status.ready"));
  private final JLabel filterLabel = new JLabel(" ");
  private final MergeRequestTableModel tableModel = new MergeRequestTableModel();
  private final JTable table = new JTable(tableModel);
  private final MergeRequestDetailPanel detailPanel;
  private @Nullable JSplitPane mainSplit;
  private String nextCursor;
  private boolean hasNextPage;
  /** 自己的 busy 状态（不再用「刷新按钮是否禁用」推断，避免与控件状态耦合）。 */
  private boolean busy;

  public GitLabMergePanel(Project project) {
    this.project = project;
    this.detailPanel = new MergeRequestDetailPanel(project, this::service, this::currentProjectPath,
                                                   () -> reload(false));
    buildUi();
    prefillFromSettings();
    initializing = false;
    // VCS 初始化可能晚于工具窗口创建：首屏为空时延时重试一次，否则用户必须手点刷新才有项目下拉
    // IDEA 启动初期 Git 仓库索引可能尚未建完（表现为项目下拉只到 1 个），
    // 所以分几次重扫；数量有变化就刷新服务器标签，稳定后自然停止。
    scheduleProjectChoicesReload();
    // 设置页 Apply 后就地重建 client 并重载，不用用户再手点刷新
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(
        com.hakil.gitlabplus.settings.SettingsChangedNotifier.TOPIC,
        () -> javax.swing.SwingUtilities.invokeLater(() -> {
          if (!root.isShowing()) {
            return;
          }
          refreshProjectChoices();
          updateServerLabel();
          reload(true);
        }));
    if (!currentProjectPath().isBlank() && GitLabPlusSettings.getInstance().getToken() != null) {
      ApplicationManager.getApplication().invokeLater(() -> reload(false));
    }
  }

  public JComponent component() {
    return root;
  }

  private void buildUi() {
    // 顶部：项目/环境 + 主操作；搜索框下放到列表区上方
    JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
    top.add(new JLabel(GitLabPlusBundle.message("list.project") + ":"));
    projectCombo.setEditable(true);
    projectCombo.setPreferredSize(new Dimension(240, projectCombo.getPreferredSize().height));
    projectCombo.setMinimumSize(new Dimension(140, projectCombo.getPreferredSize().height));
    projectCombo.setToolTipText("当前 IDEA 中所有 Git 仓库对应的 GitLab 项目；也可以直接输入其他项目全路径");
    projectCombo.addActionListener(e -> onProjectChanged());
    top.add(projectCombo);
    top.add(new JLabel(GitLabPlusBundle.message("list.state") + ":"));
    top.add(stateCombo);
    top.add(createButton);
    top.add(refreshButton);

    JPanel filterRow = new JPanel(new BorderLayout());
    filterRow.add(top, BorderLayout.NORTH);

    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setRowHeight(22);
    table.getTableHeader().setReorderingAllowed(false);
    // 列宽自适应：上下布局下列表占满宽度，无需横向滚动
    table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
    DefaultTableCellRenderer rendererCenter = new DefaultTableCellRenderer();
    rendererCenter.setHorizontalAlignment(SwingConstants.CENTER);
    int[] columnWidths = {55, 380, 300, 90, 80, 70};
    int[] columnMinWidths = {45, 180, 150, 70, 60, 50};
    for (int i = 0; i < columnWidths.length; i++) {
      var column = table.getColumnModel().getColumn(i);
      column.setPreferredWidth(columnWidths[i]);
      column.setMinWidth(columnMinWidths[i]);
    }
    table.getColumnModel().getColumn(0).setCellRenderer(rendererCenter);
    table.getColumnModel().getColumn(4).setCellRenderer(rendererCenter);
    table.getColumnModel().getColumn(5).setCellRenderer(rendererCenter);

    // 搜索框紧邻列表：它作用于列表内容，放在顶部会让人误以为是全局搜索
    JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
    searchRow.add(new JLabel(GitLabPlusBundle.message("list.search") + ":"));
    searchRow.add(searchField);
    searchRow.add(filterLabel);

    JPanel bottom = new JPanel(new BorderLayout(6, 0));
    JPanel bottomLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
    bottomLeft.add(loadMoreButton);
    bottomLeft.add(statusLabel);
    bottom.add(bottomLeft, BorderLayout.WEST);
    // 自检是低频诊断入口，放到列表底部的最右侧，不占主操作位
    JPanel bottomRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
    bottomRight.add(selfCheckButton);
    bottom.add(bottomRight, BorderLayout.EAST);

    JPanel listPanel = new JPanel(new BorderLayout());
    listPanel.add(searchRow, BorderLayout.NORTH);
    listPanel.add(new JBScrollPane(table), BorderLayout.CENTER);
    // 加载更多 + 数量描述跟随列表底部：选中详情时它们随列表一起上浮
    listPanel.add(bottom, BorderLayout.SOUTH);
    listPanel.setMinimumSize(new Dimension(200, 70));
    root.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
    root.add(filterRow, BorderLayout.NORTH);
    // 上下布局：列表为主（初始约 62% 高度），详情仅在选中 MR 后才浮现
    mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, listPanel, detailPanel.component());
    mainSplit.setResizeWeight(0.6);
    mainSplit.setContinuousLayout(true);
    mainSplit.setOneTouchExpandable(true);
    detailPanel.component().setMinimumSize(new Dimension(200, 70));
    javax.swing.SwingUtilities.invokeLater(() -> {
      if (mainSplit != null) {
        mainSplit.setDividerLocation(0.6);
      }
    });
    // 工具窗口被压矮（如在底部打开 Git 面板）时，按比例纠正分隔位，避免详情区被挤成 0
    SplitRatioKeeper.keep(mainSplit, 0.6, 32, () -> detailPanel.component().isVisible());
    root.add(mainSplit, BorderLayout.CENTER);
    showDetail(false);
    // 不设 preferredSize：否则工具窗口无法缩窄；只给合理的最小尺寸
    root.setMinimumSize(new Dimension(280, 160));

    refreshButton.addActionListener(e -> {
      refreshProjectChoices();
      reload(true);
    });
    selfCheckButton.addActionListener(e -> selfCheck());
    createButton.addActionListener(e -> createMergeRequest());
    stateCombo.addActionListener(e -> {
      // 初始化/预填阶段不要触发加载，否则会多打一次接口甚至递归
      if (initializing || refreshingChoices || isBusy()) {
        return;
      }
      reload(true);
    });
    loadMoreButton.addActionListener(e -> loadMore());
    searchField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        applyFilter();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        applyFilter();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        applyFilter();
      }
    });
    table.getSelectionModel().addListSelectionListener(e -> {
      if (e.getValueIsAdjusting()) {
        return;
      }
      updateButtons();
      MergeRequestInfo row = selectedRow();
      showDetail(row != null);
      detailPanel.load(row == null ? null : row.iid);
    });
    updateButtons();
  }

  /** 只读自检：一次验证 token / 项目 / GraphQL / changes / discussions 五条链路。 */
  private void selfCheck() {
    GitLabPlusSettings settings = GitLabPlusSettings.getInstance();
    String server = currentServerUrl();
    String token = settings.getToken();
    String projectPath = currentProjectPath();
    if (server.isEmpty() || token == null || token.isBlank()) {
      Messages.showWarningDialog(project,
          "请先配置 GitLab 服务器与 Access Token（Settings | Tools | GitLab+ Merge Tool）。",
          "GitLab+ 自检");
      return;
    }
    if (projectPath.isEmpty()) {
      Messages.showWarningDialog(project, "请先填写项目全路径。", "GitLab+ 自检");
      return;
    }
    setBusy(true, GitLabPlusBundle.message("status.selfChecking"));
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      List<SelfCheckService.Step> steps;
      try {
        steps = SelfCheckService.run(new GitLabClient(server, token), projectPath);
      }
      catch (Exception t) {
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, GitLabPlusBundle.message("status.selfCheckFailed"));
          showError("自检失败", t);
        });
        return;
      }
      String report = SelfCheckService.report(steps);
      UIUtil.invokeLaterIfNeeded(() -> {
        setBusy(false, GitLabPlusBundle.message("status.selfCheckDone"));
        Messages.showInfoMessage(project, report, "GitLab+ 自检结果");
      });
    });
  }

  /** 从当前分支创建 merge request（REST v4，与官方字段集对齐）。 */
  private void createMergeRequest() {
    GitLabService service = service();
    if (service == null) {
      return;
    }
    String path = currentProjectPath();
    if (path.isEmpty()) {
      Messages.showWarningDialog(project, "请先选择或填写项目全路径。", "GitLab+ 新建 MR");
      return;
    }
    String source = BranchLister.currentBranch(project, path);
    if (source == null) {
      Messages.showWarningDialog(project, "当前项目不在任何 Git 分支上，无法创建 merge request。", "GitLab+ 新建 MR");
      return;
    }
    BranchLister.Branches branches = BranchLister.forProject(project, path);
    java.io.File workDir = BranchLister.workDirFor(project, path);
    java.util.Map<String, String> variables = new java.util.LinkedHashMap<>();
    variables.put("source", source);
    variables.put("target", lastTargetBranch());
    variables.put("branchTitle", TemplateRenderer.branchTitle(source));
    variables.put("project", path);
    GitLabPlusSettings settings = GitLabPlusSettings.getInstance();
    CreateMergeRequestDialog dialog = new CreateMergeRequestDialog(
        project,
        branches,
        workDir,
        source,
        lastTargetBranch(),
        settings.getTitleTemplate(),
        settings.getDescriptionTemplate(),
        variables,
        settings.isDefaultRemoveSourceBranch(),
        settings.isDefaultSquashOnMerge());
    if (!dialog.showAndGet()) {
      return;
    }
    String target = dialog.targetBranch();
    String sourceBranch = dialog.sourceBranch();
    PropertiesComponent.getInstance().setValue(KEY_TARGET_BRANCH, target);
    // 是否要推送：不能只看远程有无同名分支 —— 远程有旧分支但本地有新提交时也必须 push，
    // 否则创建出的 MR 不含任何变更（已实测的缺陷）。
    boolean needsPush = workDir == null
        ? branches.needsPush(sourceBranch)
        : BranchPusher.aheadCount(workDir, sourceBranch) != 0;
    setBusy(true, needsPush ? "推送 " + sourceBranch + "…" : "创建 merge request…");
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        // 本地分支未推送时先推送：拒绝（远程有新提交）则停下，要求先 Pull 解决冲突
        if (needsPush) {
          BranchPusher.Result push = BranchPusher.push(project, sourceBranch);
          if (!push.pushed()) {
            String advice = BranchPusher.advice(push, sourceBranch);
            UIUtil.invokeLaterIfNeeded(() -> {
              setBusy(false, push.rejected() ? "推送被拒绝，未创建 MR" : "推送失败，未创建 MR");
              Messages.showErrorDialog(project, advice, "GitLab+ 需要先推送");
            });
            return;
          }
        }
        GitLabService.Created created = service.createMergeRequest(
            path, dialog.sourceBranch(), target, dialog.title(), dialog.description(),
            dialog.removeSourceBranch(), dialog.squash());        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, GitLabPlusBundle.message("status.mrCreated", created.iid()));
          // 不自动弹浏览器：创建是高频动作，抢焦点会打断当前工作。
          // 通知里保留 MR 号，需要时在列表选中后点「更多 → 打开网页」。
          NotificationGroupManager.getInstance()
              .getNotificationGroup(NOTIFICATION_GROUP)
              .createNotification("GitLab+",
                                 "已创建 merge request !" + created.iid()
                                     + (created.webUrl() == null || created.webUrl().isBlank()
                                            ? "" : "\n" + created.webUrl()),
                                 NotificationType.INFORMATION)
              .notify(project);
          reload(true);
        });
      }
      catch (Exception t) {
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, GitLabPlusBundle.message("status.actionFailed", GitLabPlusBundle.message("list.newMr")));
          showError("创建 merge request 失败", t);
        });
      }
    });
  }


  private String lastTargetBranch() {
    String stored = PropertiesComponent.getInstance().getValue(KEY_TARGET_BRANCH);
    return stored == null || stored.isBlank() ? "dev" : stored;
  }


  /** 详情区是否显示：未选中 MR 时隐藏（列表占满），选中后浮现并占下方约 38%。 */
  private void showDetail(boolean show) {
    JComponent detail = detailPanel.component();
    if (detail.isVisible() == show) {
      return;
    }
    detail.setVisible(show);
    if (mainSplit != null) {
      mainSplit.setResizeWeight(show ? 0.6 : 1.0);
      mainSplit.setDividerLocation(show ? 0.6 : 1.0);
    }
  }

  private void applyFilter() {
    tableModel.setFilter(searchField.getText());
    filterLabel.setText("已显示 " + tableModel.visibleCount() + " / " + tableModel.totalCount() + " 条");
  }

  private void prefillFromSettings() {
    GitLabPlusSettings settings = GitLabPlusSettings.getInstance();
    refreshProjectChoices();
    // 列表默认状态来自设置（默认 opened）
    stateCombo.setSelectedItem(settings.getDefaultMrState());

    String configuredPath = settings.getProjectPath();
    if (configuredPath != null && !configuredPath.isBlank()) {
      projectCombo.getEditor().setItem(configuredPath);
      updateServerLabel();
      return;
    }
    if (projectCombo.getItemCount() > 0) {
      projectCombo.setSelectedIndex(0);
      updateServerLabel();
      return;
    }
    statusLabel.setText("未能从 git remote 解析项目路径，请手动填写");
    updateServerLabel();
  }

  /** 服务器地址标签跟随项目下拉（地址修改统一在设置页）。 */
  private void updateServerLabel() {
    String server = currentServerUrl();
    serverLabel.setText(server.isBlank() ? " " : server);
    serverLabel.setToolTipText(server);
  }

  /** 项目切换：同步服务器标签，值真正变化时自动刷新列表。 */
  private void onProjectChanged() {
    if (initializing || refreshingChoices) {
      return;
    }
    updateServerLabel();
    String path = currentProjectPath();
    if (!path.isBlank() && !path.equals(lastLoadedPath) && !isBusy()) {
      reload(true);
    }
  }

  private String currentServerUrl() {
    String mapped = projectServerMap.get(currentProjectPath());
    if (mapped != null && !mapped.isBlank()) {
      return mapped;
    }
    String fallback = GitLabPlusSettings.getInstance().getServerUrl();
    return fallback == null ? "" : fallback.trim();
  }

  /**
   * 用当前 IDEA 中所有 Git 仓库的 remote 重建项目下拉（保留用户正在编辑的值）。
   *
   * <p>按设置做过滤（逗号分隔关键字）与排序（name / path / recent）。</p>
   */
  private void refreshProjectChoices() {
    Object current = currentProjectPathRaw();
    boolean previous = refreshingChoices;
    refreshingChoices = true;
    try {
      GitLabPlusSettings settings = GitLabPlusSettings.getInstance();
      projectServerMap.clear();
      projectCombo.removeAllItems();
      for (GitProjectResolver.Resolved item : GitProjectResolver.resolveAll(project)) {
        projectServerMap.put(item.projectPath(), item.serverUrl());
      }
      // 下拉内容与顺序由设置里的双列表决定（未配置 = 全部，首次开箱即用）
      java.util.List<String> available = new java.util.ArrayList<>(projectServerMap.keySet());
      for (String path : ProjectSelection.ordered(settings.getDropdownProjects(), available)) {
        projectCombo.addItem(path);
      }
      if (current != null && !current.toString().isBlank()) {
        projectCombo.getEditor().setItem(current.toString());
      }
    }
    finally {
      refreshingChoices = previous;
    }
  }

  /**
   * 启动/首次打开时多次重扫项目下拉。
   *
   * <p>{@code GitRepositoryManager} 在 IDEA 启动早期可能只返回当前项目，
   * 等后台索引建完才包含其它仓库；固定顺序的几次重试能兑付“首次只有 1 个项目”的体验。</p>
   */
  private void scheduleProjectChoicesReload() {
    for (int delay : new int[]{800, 2500, 6000, 12000}) {
      javax.swing.Timer timer = new javax.swing.Timer(delay, e -> {
        if (!root.isShowing()) {
          return;
        }
        int before = projectCombo.getItemCount();
        refreshProjectChoices();
        if (projectCombo.getItemCount() != before) {
          updateServerLabel();
        }
      });
      timer.setRepeats(false);
      timer.setInitialDelay(delay);
      timer.start();
    }
  }


  private void updateButtons() {
    loadMoreButton.setEnabled(!isBusy() && hasNextPage);
  }

  @Override
  public void dispose() {
    // 订阅随本面板的 Disposable 生命周期释放
    busy = false;
  }

  private boolean isBusy() {
    return busy;
  }

  private void setBusy(boolean busy, String status) {
    this.busy = busy;
    refreshButton.setEnabled(!busy);
    stateCombo.setEnabled(!busy);
    searchField.setEnabled(!busy);
    loadMoreButton.setEnabled(!busy && hasNextPage);
    if (status != null) {
      statusLabel.setText(status);
    }
  }

  @Nullable
  private MergeRequestInfo selectedRow() {
    return tableModel.at(table.getSelectedRow());
  }

  private @Nullable GitLabService service() {
    GitLabPlusSettings settings = GitLabPlusSettings.getInstance();
    String server = currentServerUrl();
    String token = settings.getToken();
    if (server.isEmpty()) {
      Messages.showWarningDialog(project, "请先填写 GitLab 服务器地址", "GitLab+");
      return null;
    }
    if (token == null || token.isBlank()) {
      Messages.showWarningDialog(project,
          "未配置 Access Token。请到 Settings | Tools | GitLab+ Merge Tool 填写（需 api 权限）。",
          "GitLab+");
      return null;
    }
    return new GitLabService(server, token);
  }

  private String currentProjectPath() {
    Object value = currentProjectPathRaw();
    return value == null ? "" : value.toString().trim();
  }

  /** 下拉可编辑：优先取编辑器内容，否则取选中项。 */
  private @Nullable Object currentProjectPathRaw() {
    Object value = projectCombo.isEditable()
        ? projectCombo.getEditor().getItem()
        : projectCombo.getSelectedItem();
    if (value == null || value.toString().isBlank()) {
      value = projectCombo.getSelectedItem();
    }
    return value;
  }

  /** 加载列表；用户刷新时把当前输入写回配置。 */
  private void reload(boolean fromUser) {
    if (fromUser) {
      GitLabPlusSettings.getInstance().setServerUrl(currentServerUrl());
      GitLabPlusSettings.getInstance().setProjectPath(currentProjectPath());
    }
    GitLabService service = service();
    if (service == null) {
      return;
    }
    String projectPath = currentProjectPath();
    if (projectPath.isEmpty()) {
      Messages.showWarningDialog(project, "请填写项目全路径，例如 group/sub/project", "GitLab+");
      return;
    }
    lastLoadedPath = projectPath;

    setBusy(true, GitLabPlusBundle.message("status.loadingMr"));
    String state = MergeRequestFilter.toGraphQLState((String) stateCombo.getSelectedItem());
    nextCursor = null;
    hasNextPage = false;
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        GitLabService.Page page = service.listMergeRequests(projectPath, state, null);
        UIUtil.invokeLaterIfNeeded(() -> {
          tableModel.setRows(page.items());
          nextCursor = page.endCursor();
          hasNextPage = page.hasNextPage();
          setBusy(false, describeCount());
          updateButtons();
        });
      }
      catch (Exception t) {
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, GitLabPlusBundle.message("status.loadFailed", GitLabPlusBundle.message("noun.mr")) + "：" + rootMessage(t));
          showError("加载 merge request 失败", t);
        });
      }
    });
  }

  /** 取下一页并追加（按 iid 去重）。 */
  private void loadMore() {
    GitLabService service = service();
    if (service == null) {
      return;
    }
    String cursor = nextCursor;
    if (cursor == null || cursor.isBlank()) {
      return;
    }
    String projectPath = currentProjectPath();
    String state = MergeRequestFilter.toGraphQLState((String) stateCombo.getSelectedItem());
    setBusy(true, GitLabPlusBundle.message("list.loadMore") + "…");
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        GitLabService.Page page = service.listMergeRequests(projectPath, state, cursor);
        UIUtil.invokeLaterIfNeeded(() -> {
          tableModel.appendRows(page.items());
          nextCursor = page.endCursor();
          hasNextPage = page.hasNextPage();
          setBusy(false, describeCount());
          updateButtons();
        });
      }
      catch (Exception t) {
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, GitLabPlusBundle.message("status.loadMoreFailed") + "：" + rootMessage(t));
          showError("加载更多失败", t);
        });
      }
    });
  }

  private String describeCount() {
    String base = "共 " + tableModel.totalCount() + " 条";
    if (tableModel.visibleCount() != tableModel.totalCount()) {
      base += "（筛选后 " + tableModel.visibleCount() + "）";
    }
    return hasNextPage ? base + "，还有更多" : base;
  }



  private void showError(String title, Throwable t) {
    String message = t instanceof GitLabClient.GitLabApiException ? t.getMessage() : rootMessage(t);
    Messages.showErrorDialog(project, message, title);
  }

  private static String rootMessage(Throwable t) {
    Throwable current = t;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    String message = current.getMessage();
    return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
  }


  /** 表格模型：累积分页数据 + 客户端搜索过滤。 */
  private static final class MergeRequestTableModel extends AbstractTableModel {
    private final String[] columns = {"!", "标题", "分支", "状态", "可合并", "Draft"};
    private final List<MergeRequestInfo> allRows = new ArrayList<>();
    private final List<MergeRequestInfo> visibleRows = new ArrayList<>();
    private String filter = "";

    void setRows(List<MergeRequestInfo> newRows) {
      allRows.clear();
      allRows.addAll(newRows);
      refilter();
    }

    void appendRows(List<MergeRequestInfo> more) {
      List<MergeRequestInfo> merged = MergeRequestFilter.mergeDistinct(allRows, more);
      allRows.clear();
      allRows.addAll(merged);
      refilter();
    }

    void setFilter(String text) {
      filter = text == null ? "" : text;
      refilter();
    }

    private void refilter() {
      visibleRows.clear();
      visibleRows.addAll(MergeRequestFilter.filter(allRows, filter));
      fireTableDataChanged();
    }

    int totalCount() {
      return allRows.size();
    }

    int visibleCount() {
      return visibleRows.size();
    }

    @Nullable
    MergeRequestInfo at(int index) {
      if (index < 0 || index >= visibleRows.size()) {
        return null;
      }
      return visibleRows.get(index);
    }

    @Override
    public int getRowCount() {
      return visibleRows.size();
    }

    @Override
    public int getColumnCount() {
      return columns.length;
    }

    @Override
    public String getColumnName(int column) {
      return columns[column];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      MergeRequestInfo info = visibleRows.get(rowIndex);
      return switch (columnIndex) {
        case 0 -> info.iid;
        case 1 -> info.shortTitle();
        case 2 -> info.displayBranch();
        case 3 -> info.state;
        case 4 -> info.mergeable ? "是" : "否";
        case 5 -> info.draft ? "是" : "";
        default -> "";
      };
    }
  }
}
