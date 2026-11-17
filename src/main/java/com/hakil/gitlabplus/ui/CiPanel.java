package com.hakil.gitlabplus.ui;

import com.hakil.gitlabplus.api.GitLabClient;
import com.hakil.gitlabplus.i18n.GitLabPlusBundle;
import com.hakil.gitlabplus.model.PipelineInfo;
import com.hakil.gitlabplus.model.PipelineJob;
import com.hakil.gitlabplus.model.PipelineRole;
import com.hakil.gitlabplus.model.PipelineState;
import com.hakil.gitlabplus.service.GitLabService;
import com.hakil.gitlabplus.service.PipelineWatchService;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * CI 面板：上方用表格列出该 MR 关联的**全部**流水线（源分支 / 合并提交 / 目标分支），
 * 下方是该流水线的 job 列表，支持运行、重跑、取消与关注通知。
 *
 * <p>用表格而不是下拉：一个 MR 常有 3~10 条 pipeline（每次提交一条 + 合并后一条），
 * 下拉只能看到当前一条，无法一眼看清状态与来源。</p>
 */
public final class CiPanel {

  private final Project project;
  private final Supplier<GitLabService> serviceSupplier;
  private final Supplier<String> projectPathSupplier;

  private final JPanel root = new JPanel(new BorderLayout(0, 4));
  private final PipelineTableModel pipelineModel = new PipelineTableModel();
  private final JTable pipelineTable = new JTable(pipelineModel);
  private final JobTableModel jobModel = new JobTableModel();
  private final JTable jobTable = new JTable(jobModel);
  private final JLabel selectedLabel = new JLabel(" ");
  private final JButton reloadButton = new JButton(GitLabPlusBundle.message("ci.reload"));
  private final JButton watchButton = new JButton(GitLabPlusBundle.message("ci.watch"));
  private final JButton playButton = new JButton(GitLabPlusBundle.message("ci.playJob"));
  private final JButton cancelJobButton = new JButton(GitLabPlusBundle.message("ci.cancelJob"));
  private final JButton openButton = new JButton(GitLabPlusBundle.message("ci.openWeb"));
  private final JButton logButton = new JButton(GitLabPlusBundle.message("ci.viewLog"));
  private final JButton moreButton = new JButton(GitLabPlusBundle.message("action.more") + " \u25BE");
  private final JLabel statusLabel = new JLabel(" ");

  private @Nullable String iid;
  /** 当前 MR 的分支/合并提交上下文，用于把流水线标成「合并结果 / 目标分支 / MR 校验 / 源分支」。 */
  private String ctxSourceBranch = "";
  private String ctxTargetBranch = "";
  private String ctxMergeSha = "";
  /** 关注轮循回推的最新状态（覆盖列表里的初始值）。 */
  private final Map<String, PipelineInfo> pipelineOverrides = new HashMap<>();
  /** 填充表格期间抑制选择事件，避免递归加载。 */
  private boolean filling;
  private boolean busy;

  public CiPanel(Project project,
                 Supplier<GitLabService> serviceSupplier,
                 Supplier<String> projectPathSupplier) {
    this.project = project;
    this.serviceSupplier = serviceSupplier;
    this.projectPathSupplier = projectPathSupplier;
    buildUi();
    project.getMessageBus().connect().subscribe(PipelineWatchService.TOPIC,
        (PipelineWatchService.Listener) (watchIid, info, finished) ->
            UIUtil.invokeLaterIfNeeded(() -> onWatchUpdate(watchIid, info, finished)));
  }

  public JComponent component() {
    return root;
  }

  private void buildUi() {
    // ---- 流水线表 ----
    pipelineTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    pipelineTable.setRowHeight(22);
    pipelineTable.getTableHeader().setReorderingAllowed(false);
    pipelineTable.setDefaultRenderer(Object.class, new StatusRenderer());
    int[] pipelineWidths = {60, 110, 260, 110, 90, 130};
    String[] pipelineHeaders = {"#", "状态", "触发", "来源", "SHA", "创建时间"};
    for (int i = 0; i < pipelineWidths.length; i++) {
      pipelineTable.getColumnModel().getColumn(i).setPreferredWidth(pipelineWidths[i]);
      pipelineTable.getColumnModel().getColumn(i).setHeaderValue(pipelineHeaders[i]);
    }
    pipelineTable.getSelectionModel().addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting() && !filling) {
        onPipelineSelected();
      }
    });

    JPanel pipelinePanel = new JPanel(new BorderLayout(0, 2));
    JPanel pipelineHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
    pipelineHeader.add(new JLabel(GitLabPlusBundle.message("ci.label.pipelines")));
    pipelineHeader.add(reloadButton);
    pipelineHeader.add(watchButton);
    pipelineHeader.add(selectedLabel);
    pipelinePanel.add(pipelineHeader, BorderLayout.NORTH);
    pipelinePanel.add(new JBScrollPane(pipelineTable), BorderLayout.CENTER);
    pipelinePanel.setMinimumSize(new Dimension(200, 60));

    // ---- Job 表 ----
    jobTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    jobTable.setRowHeight(22);
    jobTable.getTableHeader().setReorderingAllowed(false);
    jobTable.setDefaultRenderer(Object.class, new StatusRenderer());
    int[] jobWidths = {240, 110, 90, 80};
    String[] jobHeaders = {"Job", "Stage", "状态", "耗时"};
    for (int i = 0; i < jobWidths.length; i++) {
      jobTable.getColumnModel().getColumn(i).setPreferredWidth(jobWidths[i]);
      jobTable.getColumnModel().getColumn(i).setHeaderValue(jobHeaders[i]);
    }
    jobTable.getSelectionModel().addListSelectionListener(e -> updateButtons());

    JPanel jobPanel = new JPanel(new BorderLayout(0, 2));
    jobPanel.add(new JLabel(GitLabPlusBundle.message("ci.label.jobs")), BorderLayout.NORTH);
    jobPanel.add(new JBScrollPane(jobTable), BorderLayout.CENTER);
    jobPanel.setMinimumSize(new Dimension(200, 36));

    // 流水线表是主体（含合并前后多条），job 表辅助：给流水线更大权重与初始高度
    JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, pipelinePanel, jobPanel);
    split.setResizeWeight(0.68);
    split.setContinuousLayout(true);
    split.setOneTouchExpandable(true);
    split.setDividerLocation(0.68);
    SplitRatioKeeper.keep(split, 0.68, 32);

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
    actions.add(playButton);
    actions.add(cancelJobButton);
    actions.add(openButton);
    actions.add(logButton);
    actions.add(moreButton);
    actions.add(statusLabel);

    root.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    root.add(split, BorderLayout.CENTER);
    root.add(actions, BorderLayout.SOUTH);

    reloadButton.addActionListener(e -> load(iid));
    watchButton.addActionListener(e -> toggleWatch());
    playButton.addActionListener(e -> playOrRetrySelectedJob());
    cancelJobButton.addActionListener(e -> cancelSelectedJob());
    openButton.addActionListener(e -> openSelected());
    // 双击 job 直接看日志，减少一次点击
    jobTable.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        if (e.getClickCount() == 2 && jobTable.getSelectedRow() >= 0) {
          openJobLog();
        }
      }
    });
    logButton.addActionListener(e -> openJobLog());
    moreButton.addActionListener(e -> showMoreMenu());
    updateButtons();
  }

  // ------------------------------------------------------------------ 数据

  public void load(@Nullable String iid) {
    this.iid = iid;
    pipelineOverrides.clear();
    filling = true;
    try {
      pipelineModel.setRows(List.of());
    }
    finally {
      filling = false;
    }
    jobModel.setRows(List.of());
    selectedLabel.setText(" ");
    updateButtons();
    if (iid == null || iid.isBlank()) {
      statusLabel.setText(" ");
      return;
    }
    GitLabService service = serviceSupplier.get();
    String path = projectPath();
    if (service == null || path == null) {
      return;
    }
    setBusy(true, GitLabPlusBundle.message("status.loadingPipelines"));
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        GitLabService.RelatedPipelines related = service.relatedPipelines(path, iid);
        List<PipelineInfo> pipelines = related.pipelines();
        UIUtil.invokeLaterIfNeeded(() -> {
          ctxSourceBranch = related.sourceBranch() == null ? "" : related.sourceBranch();
          ctxTargetBranch = related.targetBranch() == null ? "" : related.targetBranch();
          ctxMergeSha = related.mergeSha() == null ? "" : related.mergeSha();
          pipelineModel.setContext(ctxSourceBranch, ctxTargetBranch, ctxMergeSha);
          filling = true;
          try {
            pipelineModel.setRows(pipelines);
            if (!pipelines.isEmpty()) {
              pipelineTable.setRowSelectionInterval(0, 0);
            }
          }
          finally {
            filling = false;
          }
          setBusy(false, pipelines.isEmpty() ? "该 MR 没有关联的流水线" : "共 " + pipelines.size() + " 条流水线");
          onPipelineSelected();
        });
      }
      catch (Exception t) {
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, GitLabPlusBundle.message("status.loadPipelinesFailed"));
          showError("加载流水线失败", t);
        });
      }
    });
  }

  private void onPipelineSelected() {
    PipelineInfo info = selectedPipeline();
    if (info == null) {
      jobModel.setRows(List.of());
      selectedLabel.setText(" ");
      updateButtons();
      return;
    }
    renderSelected(info);
    loadJobs(info);
  }

  private void loadJobs(PipelineInfo info) {
    GitLabService service = serviceSupplier.get();
    String path = projectPath();
    if (service == null || path == null) {
      return;
    }
    setBusy(true, GitLabPlusBundle.message("status.loadingJobs"));
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        List<PipelineJob> jobs = service.pipelineJobs(path, info.id());
        UIUtil.invokeLaterIfNeeded(() -> {
          jobModel.setRows(jobs);
          long manualCount = jobs.stream().filter(PipelineJob::isManual).count();
          setBusy(false, jobs.size() + " 个 job"
              + (manualCount > 0 ? "，其中 " + manualCount + " 个等待手动运行" : ""));
          renderSelected(selectedPipeline());
          updateButtons();
        });
      }
      catch (Exception t) {
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, GitLabPlusBundle.message("status.loadJobsFailed"));
          showError("加载 job 失败", t);
        });
      }
    });
  }

  private void renderSelected(@Nullable PipelineInfo info) {
    if (info == null) {
      selectedLabel.setText(" ");
      return;
    }
    PipelineInfo effective = pipelineOverrides.getOrDefault(info.id(), info);
    PipelineWatchService watch = PipelineWatchService.getInstance();
    String path = projectPath();
    boolean watchingThis = path != null && iid != null
        && effective.id().equals(watch.watchedPipelineId(path, iid));
    String text = "#" + effective.id() + "  " + effective.displayStatus()
        + (effective.sourceLabel().isEmpty() ? "" : "  · " + effective.sourceLabel())
        + (watchingThis ? "  · 关注中（剩余 " + watch.remainingMinutes(path, iid) + " 分钟）" : "");
    selectedLabel.setText(text);
    selectedLabel.setForeground(colorFor(effective.status()));
  }

  private void onWatchUpdate(String watchIid, PipelineInfo info, boolean finished) {
    if (info == null || watchIid == null || iid == null || !watchIid.equals(iid)) {
      return;
    }
    pipelineOverrides.put(info.id(), info);
    filling = true;
    try {
      pipelineModel.replace(info);
    }
    finally {
      filling = false;
    }
    renderSelected(selectedPipeline());
    if (finished) {
      statusLabel.setText("流水线已结束：" + PipelineState.displayName(info.status())
                              + "（失败 " + countFailed() + "）");
      PipelineInfo selected = selectedPipeline();
      if (selected != null && selected.id().equals(info.id())) {
        loadJobs(selected);
      }
    }
    updateButtons();
  }

  private int countFailed() {
    int failed = 0;
    for (int i = 0; i < jobModel.getRowCount(); i++) {
      if ("failed".equalsIgnoreCase(String.valueOf(jobModel.getValueAt(i, 2)))) {
        failed++;
      }
    }
    return failed;
  }

  // ------------------------------------------------------------------ 操作

  private void playOrRetrySelectedJob() {
    PipelineJob job = selectedJob();
    GitLabService service = serviceSupplier.get();
    String path = projectPath();
    if (job == null || service == null || path == null) {
      return;
    }
    boolean play = job.canPlay();
    String action = play ? "触发" : "重跑";
    setBusy(true, action + " job " + job.name() + "…");
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        if (play) {
          service.playJob(path, job.id());
        }
        else {
          service.retryJob(path, job.id());
        }
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, GitLabPlusBundle.message("status.jobActionRequested", action + " " + job.name()));
          PipelineInfo info = selectedPipeline();
          if (info != null) {
            loadJobs(info);
          }
        });
      }
      catch (Exception t) {
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, action + " job 失败");
          showError(action + " job 失败", t);
        });
      }
    });
  }

  private void cancelSelectedJob() {
    PipelineJob job = selectedJob();
    GitLabService service = serviceSupplier.get();
    String path = projectPath();
    if (job == null || service == null || path == null) {
      return;
    }
    if (Messages.showYesNoDialog(project, "确定取消 job「" + job.name() + "」？",
                                 "GitLab+ 取消 job", Messages.getQuestionIcon()) != Messages.YES) {
      return;
    }
    setBusy(true, GitLabPlusBundle.message("status.cancelRequested", job.name()));
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        service.cancelJob(path, job.id());
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, GitLabPlusBundle.message("status.cancelRequested", job.name()));
          PipelineInfo info = selectedPipeline();
          if (info != null) {
            loadJobs(info);
          }
        });
      }
      catch (Exception t) {
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, GitLabPlusBundle.message("status.cancelFailed"));
          showError("取消 job 失败", t);
        });
      }
    });
  }

  private void showMoreMenu() {
    JPopupMenu menu = new JPopupMenu();
    PipelineInfo info = selectedPipeline();
    boolean hasPipeline = info != null && !busy;

    JMenuItem retryPipeline = new JMenuItem("重跑整条流水线");
    retryPipeline.setEnabled(hasPipeline);
    retryPipeline.addActionListener(e -> runPipelineAction(info, true));
    menu.add(retryPipeline);

    JMenuItem cancelPipeline = new JMenuItem("取消整条流水线");
    cancelPipeline.setEnabled(hasPipeline);
    cancelPipeline.addActionListener(e -> runPipelineAction(info, false));
    menu.add(cancelPipeline);

    menu.addSeparator();
    JMenuItem openPipeline = new JMenuItem("打开流水线网页");
    openPipeline.setEnabled(info != null && info.webUrl() != null && !info.webUrl().isBlank());
    openPipeline.addActionListener(e -> {
      if (info != null) {
        BrowserUtil.browse(info.webUrl());
      }
    });
    menu.add(openPipeline);

    JMenuItem refresh = new JMenuItem("刷新流水线与 Job");
    refresh.setEnabled(!busy && iid != null);
    refresh.addActionListener(e -> load(iid));
    menu.add(refresh);

    menu.show(moreButton, 0, moreButton.getHeight());
  }

  private void runPipelineAction(PipelineInfo info, boolean retry) {
    GitLabService service = serviceSupplier.get();
    String path = projectPath();
    if (info == null || service == null || path == null) {
      return;
    }
    String label = retry ? "重跑" : "取消";
    if (!retry && Messages.showYesNoDialog(project, "确定取消整条流水线 #" + info.id() + "？",
                                           "GitLab+ 取消流水线",
                                           Messages.getQuestionIcon()) != Messages.YES) {
      return;
    }
    setBusy(true, label + "流水线 #" + info.id() + "…");
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        if (retry) {
          service.retryPipeline(path, info.id());
        }
        else {
          service.cancelPipeline(path, info.id());
        }
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, GitLabPlusBundle.message("status.pipelineActionRequested", label, info.id()));
          load(iid);
        });
      }
      catch (Exception t) {
        UIUtil.invokeLaterIfNeeded(() -> {
          setBusy(false, label + "流水线失败");
          showError(label + "流水线失败", t);
        });
      }
    });
  }

  private void toggleWatch() {
    GitLabService service = serviceSupplier.get();
    String path = projectPath();
    String currentIid = iid;
    PipelineInfo info = selectedPipeline();
    if (service == null || path == null || currentIid == null || info == null) {
      Messages.showInfoMessage(project, "请先在列表里选择一条流水线。", "GitLab+ 关注流水线");
      return;
    }
    PipelineWatchService watch = PipelineWatchService.getInstance();
    if (watch.isWatching(path, currentIid)) {
      watch.cancel(path, currentIid);
      statusLabel.setText("已取消关注");
    }
    else {
      if (PipelineState.shouldStopWatching(info.status())) {
        boolean proceed = Messages.showYesNoDialog(project,
                                                   "该流水线已是「" + info.displayStatus()
                                                       + "」，通常无需关注。仍要关注以便感知后续重跑吗？",
                                                   "GitLab+ 关注流水线",
                                                   Messages.getQuestionIcon()) == Messages.YES;
        if (!proceed) {
          return;
        }
      }
      watch.start(project, path, currentIid, info.id(), service.client());
      statusLabel.setText("已开始关注 #" + info.id() + "（剩余 "
                              + watch.remainingMinutes(path, currentIid) + " 分钟，超时自动停止）");
    }
    updateButtons();
    renderSelected(selectedPipeline());
  }

  /** 打开选中 job 的执行日志。 */
  private void openJobLog() {
    PipelineJob job = selectedJob();
    GitLabService service = serviceSupplier.get();
    String path = projectPath();
    if (job == null || service == null || path == null) {
      return;
    }
    new JobLogDialog(project, service, path, job).show();
  }

  private void openSelected() {
    PipelineJob job = selectedJob();
    if (job != null && job.webUrl() != null && !job.webUrl().isBlank()) {
      BrowserUtil.browse(job.webUrl());
      return;
    }
    PipelineInfo info = selectedPipeline();
    if (info != null && info.webUrl() != null && !info.webUrl().isBlank()) {
      BrowserUtil.browse(info.webUrl());
    }
  }

  // ------------------------------------------------------------------ 状态

  private @Nullable PipelineInfo selectedPipeline() {
    int index = pipelineTable.getSelectedRow();
    return pipelineModel.row(index);
  }

  private @Nullable PipelineJob selectedJob() {
    int index = jobTable.getSelectedRow();
    return jobModel.row(index);
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
    reloadButton.setEnabled(enabled);
    watchButton.setEnabled(enabled && selectedPipeline() != null);
    PipelineJob job = selectedJob();
    boolean canPlay = enabled && job != null && job.canPlay();
    boolean canRetry = enabled && job != null && job.canRetry();
    playButton.setEnabled(canPlay || canRetry);
    playButton.setText(canPlay ? GitLabPlusBundle.message("ci.playJob")
                               : GitLabPlusBundle.message("ci.retryJob"));
    playButton.setToolTipText(canPlay ? "手动运行该 job（网页上的运行按钮）"
                                      : "重跑选中的 job（失败/取消/成功均可）");
    cancelJobButton.setEnabled(enabled && job != null && job.canCancel());
    logButton.setEnabled(enabled && job != null);
    String path = projectPath();
    boolean watching = path != null && iid != null
        && PipelineWatchService.getInstance().isWatching(path, iid);
    watchButton.setText(watching ? GitLabPlusBundle.message("ci.unwatch")
                                 : GitLabPlusBundle.message("ci.watch"));
    openButton.setEnabled(enabled && (job != null || selectedPipeline() != null));
  }

  private @Nullable String projectPath() {
    String path = projectPathSupplier.get();
    return path == null || path.isBlank() ? null : path;
  }

  private void showError(String title, Throwable t) {
    String message = t instanceof GitLabClient.GitLabApiException ? t.getMessage() : String.valueOf(t.getMessage());
    Messages.showErrorDialog(project, message, title);
  }

  private static Color colorFor(String status) {
    return switch (PipelineState.classify(status)) {
      case SUCCESS -> new Color(0, 128, 0);
      case FAILURE -> Color.RED.darker();
      case RUNNING -> new Color(0, 100, 180);
      case NEEDS_MANUAL -> new Color(160, 110, 0);
      case UNKNOWN -> Color.DARK_GRAY;
    };
  }

  // ------------------------------------------------------------------ 表格模型
}
