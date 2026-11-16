package com.caidailian.gitlabplus.ui;

import com.caidailian.gitlabplus.i18n.GitLabPlusBundle;
import com.caidailian.gitlabplus.model.AnsiStripper;
import com.caidailian.gitlabplus.model.FontZoom;
import com.caidailian.gitlabplus.model.JobLog;
import com.caidailian.gitlabplus.model.JobLogStyle;
import com.caidailian.gitlabplus.model.PipelineJob;
import com.caidailian.gitlabplus.service.GitLabService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.Scrollable;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.EnumMap;
import java.util.Map;

/**
 * Job 执行日志（GitLab trace）查看器。
 *
 * <p>非模态、只读、等宽；按行着色（错误红 / 警告橙 / 成功绿 / 分组灰 / 命令蓝）。
 * 超长日志只渲染尾部 {@link JobLog#DEFAULT_MAX_LINES} 行，并在首行说明省略行数。</p>
 */
public final class JobLogDialog extends DialogWrapper {

  private static final Color COLOR_ERROR = new Color(0xC6, 0x28, 0x28);
  private static final Color COLOR_WARN = new Color(0xB2, 0x6A, 0x00);
  private static final Color COLOR_SUCCESS = new Color(0x2E, 0x7D, 0x32);
  private static final Color COLOR_COMMAND = new Color(0x15, 0x65, 0xC0);
  private static final Color COLOR_SECTION = new Color(0x80, 0x80, 0x80);

  private final Project project;
  private final GitLabService service;
  private final String projectPath;
  private final PipelineJob job;

  private final LogTextPane pane = new LogTextPane();
  private final JLabel status = new JLabel(" ");
  private final JButton refreshButton = new JButton(GitLabPlusBundle.message("log.refresh"));
  private final JButton toEndButton = new JButton(GitLabPlusBundle.message("log.jumpToEnd"));
  private final JButton copyButton = new JButton(GitLabPlusBundle.message("log.copyAll"));
  private final JButton resetFontButton = new JButton(GitLabPlusBundle.message("log.resetFont"));
  private final JCheckBox autoRefreshCheck = new JCheckBox(GitLabPlusBundle.message("log.autoRefresh"));
  private javax.swing.Timer autoRefreshTimer;
  private final JLabel fontLabel = new JLabel(GitLabPlusBundle.message("log.fontSize", FontZoom.DEFAULT_SIZE));
  private int fontSize = FontZoom.DEFAULT_SIZE;

  private final Map<JobLogStyle, SimpleAttributeSet> styles = new EnumMap<>(JobLogStyle.class);

  /**
   * 日志字体：优先能显示中文的等宽字体。
   *
   * <p>之前用 {@code Font.MONOSPACED}，在 Windows 上不含 CJK 字形，
   * 日志里的中文会渲染成“口”；这里按“能显示中文 + 能显示字母”挑一个候选。</p>
   */
  private static final String LOG_FONT_FAMILY = pickLogFontFamily();

  private static String pickLogFontFamily() {
    try {
      java.awt.Font editorFont = com.intellij.openapi.editor.colors.EditorColorsManager.getInstance()
          .getGlobalScheme()
          .getFont(com.intellij.openapi.editor.colors.EditorFontType.PLAIN);
      if (editorFont != null
          && canShowCjk(new java.awt.Font(editorFont.getName(), java.awt.Font.PLAIN, 12))) {
        return editorFont.getName();
      }
    }
    catch (Throwable ignored) {
      // 平台服务不可用时退回候选列表
    }
    for (String candidate : new String[]{
        "JetBrains Mono", "Consolas", "NSimSun", "SimSun", "Microsoft YaHei Mono", "Monospaced"}) {
      if (canShowCjk(new java.awt.Font(candidate, java.awt.Font.PLAIN, 12))) {
        return candidate;
      }
    }
    return java.awt.Font.MONOSPACED;
  }

  private static boolean canShowCjk(java.awt.Font font) {
    return font.canDisplay('中') && font.canDisplay('A') && font.canDisplay('|');
  }

  public JobLogDialog(Project project, GitLabService service, String projectPath, PipelineJob job) {
    super(project, false);
    this.project = project;
    this.service = service;
    this.projectPath = projectPath;
    this.job = job;
    initStyles();
    setTitle(GitLabPlusBundle.message("log.title", job.name()));
    init();
    load();
  }

  /** 只保留一个「关闭」按钮：默认对话框会同时给出 OK 与 Cancel。 */
  @Override
  protected Action[] createActions() {
    return new Action[]{new DialogWrapperExitAction(GitLabPlusBundle.message("log.close"), CLOSE_EXIT_CODE)};
  }

  private void initStyles() {
    for (JobLogStyle style : JobLogStyle.values()) {
      SimpleAttributeSet attributes = new SimpleAttributeSet();
      StyleConstants.setFontFamily(attributes, LOG_FONT_FAMILY);
      StyleConstants.setFontSize(attributes, fontSize);
      StyleConstants.setForeground(attributes, colorOf(style));
      styles.put(style, attributes);
    }
  }

  private static Color colorOf(JobLogStyle style) {
    return switch (style) {
      case ERROR -> COLOR_ERROR;
      case WARN -> COLOR_WARN;
      case SUCCESS -> COLOR_SUCCESS;
      case COMMAND -> COLOR_COMMAND;
      case SECTION -> COLOR_SECTION;
      case NORMAL -> UIUtil.getLabelForeground();
    };
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    pane.setEditable(false);
    pane.setFont(new Font(LOG_FONT_FAMILY, Font.PLAIN, fontSize));

    JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
    tools.add(refreshButton);
    tools.add(toEndButton);
    tools.add(copyButton);
    tools.add(resetFontButton);
    tools.add(autoRefreshCheck);
    tools.add(fontLabel);
    tools.add(status);

    JBScrollPane scrollPane = new JBScrollPane(pane);
    scrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    // Ctrl + 滚轮缩放字号。
    // 监听器必须挂在 scrollPane 而不是 pane 上：Swing 的 LightweightDispatcher
    // 只把滚轮事件交给“最深的已注册该监听器的组件”，挂在 pane 上会抢掉
    // scrollPane 默认的滚动处理，表现为“鼠标滚轮没反应”。
    scrollPane.addMouseWheelListener(event -> {
      if (!event.isControlDown()) {
        return;
      }
      event.consume();
      applyFontSize(fontSize + (event.getWheelRotation() < 0 ? 1 : -1));
    });

    JPanel panel = new JPanel(new BorderLayout(0, 4));
    panel.add(tools, BorderLayout.NORTH);
    panel.add(scrollPane, BorderLayout.CENTER);
    panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    panel.setPreferredSize(new Dimension(900, 560));
    panel.setMinimumSize(new Dimension(420, 240));

    refreshButton.addActionListener(e -> load());
    toEndButton.addActionListener(e -> scrollToEnd());
    resetFontButton.addActionListener(e -> applyFontSize(FontZoom.DEFAULT_SIZE));
    resetFontButton.setToolTipText("恢复默认字号（也可 Ctrl + 滚轮缩放）");
    autoRefreshCheck.setToolTipText("job 处于运行中时每隔 5 秒拉一次日志（结束后自动停）");
    autoRefreshCheck.addActionListener(e -> updateAutoRefresh());
    copyButton.addActionListener(e -> {
      StringSelection selection = new StringSelection(pane.getText());
      Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
      status.setText("已复制 " + JobLog.lineCount(pane.getText()) + " 行");
    });
    resetFontButton.setEnabled(false);
    return panel;
  }

  /** 拉取 trace 并渲染（后台线程取，EDT 着色）。 */
  private void load() {
    refreshButton.setEnabled(false);
    status.setText("加载日志…");
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        String trace = service.jobTrace(projectPath, job.id());
        // 先清 ANSI / CR，再截尾：避免把转义序列截成半截留在首行
        String cleaned = AnsiStripper.strip(trace);
        String shown = JobLog.tail(cleaned, JobLog.DEFAULT_MAX_LINES);
        UIUtil.invokeLaterIfNeeded(() -> {
          boolean empty = shown == null || shown.isBlank();
          render(empty ? "（该 job 还没有日志输出）" : shown);
          scrollToEnd();
          refreshButton.setEnabled(true);
          status.setText("状态 " + job.status() + " · 共 " + JobLog.lineCount(cleaned)
                             + " 行 · 耗时 " + job.durationLabel());
        });
      }
      catch (Exception t) {
        UIUtil.invokeLaterIfNeeded(() -> {
          refreshButton.setEnabled(true);
          render("加载日志失败：\n" + t.getMessage());
          status.setText("加载失败");
        });
      }
    });
  }

  /**
   * 应用字号：样式与控件字体一起更新，并重新渲染当前日志。
   *
   * <p>StyledDocument 的字号写在每行的 AttributeSet 里，改字号必须重插文本；
   * 重插后回到末尾（日志场景下用户一般在看最后几行）。</p>
   */
  private void applyFontSize(int requested) {
    int next = FontZoom.step(requested, 0);
    if (next == fontSize) {
      return;
    }
    fontSize = next;
    initStyles();
    pane.setFont(new Font(LOG_FONT_FAMILY, Font.PLAIN, fontSize));
    fontLabel.setText(GitLabPlusBundle.message("log.fontSize", fontSize));
    resetFontButton.setEnabled(fontSize != FontZoom.DEFAULT_SIZE);
    String text = pane.getText();
    if (text != null && !text.isEmpty()) {
      render(text);
      scrollToEnd();
    }
  }

  /** 开关自动刷新：仅勾选时启动定时器。 */
  private void updateAutoRefresh() {
    if (autoRefreshTimer != null) {
      autoRefreshTimer.stop();
      autoRefreshTimer = null;
    }
    if (!autoRefreshCheck.isSelected()) {
      return;
    }
    autoRefreshTimer = new javax.swing.Timer(5000, e -> {
      if (jobRunning()) {
        load();
      }
      else {
        autoRefreshCheck.setSelected(false);
        updateAutoRefresh();
        status.setText("job 已结束，自动刷新停止");
      }
    });
    autoRefreshTimer.setRepeats(true);
    autoRefreshTimer.start();
  }

  private boolean jobRunning() {
    String status = job.status() == null ? "" : job.status().toLowerCase();
    return status.equals("running") || status.equals("pending") || status.equals("created")
        || status.equals("waiting_for_resource") || status.equals("preparing");
  }

  @Override
  public void dispose() {
    if (autoRefreshTimer != null) {
      autoRefreshTimer.stop();
      autoRefreshTimer = null;
    }
    super.dispose();
  }

  /**
   * 滚到末尾。
   *
   * <p>不能只调 {@code setCaretPosition}：{@code DefaultCaret} 默认更新策略是
   * {@code NEVER_UPDATE}，光标移动了但视口不会跟随，表现就是按钮“点了没反应”。
   * 这里额外把末尾位置算成矩形并 {@code scrollRectToVisible}。</p>
   */
  private void scrollToEnd() {
    int length = pane.getDocument().getLength();
    pane.setCaretPosition(length);
    if (length <= 0) {
      return;
    }
    try {
      java.awt.geom.Rectangle2D rect = pane.modelToView2D(length);
      if (rect != null) {
        pane.scrollRectToVisible(rect.getBounds());
      }
    }
    catch (BadLocationException ignored) {
      // 文档刚被替换时可能越界，忽略
    }
  }

  /** 按行分类着色；连续同类行合并插入，4000 行也不会卡。 */
  private void render(String text) {
    StyledDocument document = pane.getStyledDocument();
    try {
      document.remove(0, document.getLength());
      String[] lines = (text == null ? "" : text).split("\n", -1);
      JobLogStyle current = null;
      StringBuilder buffer = new StringBuilder();
      for (String line : lines) {
        JobLogStyle style = JobLogStyle.classify(line);
        if (style != current) {
          flush(document, buffer, current);
          current = style;
        }
        buffer.append(line).append('\n');
      }
      flush(document, buffer, current);
    }
    catch (BadLocationException e) {
      UIUtil.invokeLaterIfNeeded(() -> status.setText("日志渲染失败"));
    }
  }

  private void flush(StyledDocument document, StringBuilder buffer, JobLogStyle style)
      throws BadLocationException {
    if (style == null || buffer.isEmpty()) {
      buffer.setLength(0);
      return;
    }
    document.insertString(document.getLength(), buffer.toString(), styles.get(style));
    buffer.setLength(0);
  }

  /** 不自动折行：内容比视口宽时出现水平滚动条（日志按列对齐，折行会破坏可读性）。 */
  private static final class LogTextPane extends JTextPane implements Scrollable {
    @Override
    public boolean getScrollableTracksViewportWidth() {
      return getUI().getPreferredSize(this).width <= getParent().getSize().width;
    }
  }
}
