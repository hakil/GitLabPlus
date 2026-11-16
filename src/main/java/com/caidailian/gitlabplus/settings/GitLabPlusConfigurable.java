package com.caidailian.gitlabplus.settings;

import com.caidailian.gitlabplus.git.GitProjectResolver;
import com.caidailian.gitlabplus.i18n.GitLabPlusBundle;
import com.caidailian.gitlabplus.i18n.LanguageService;
import com.caidailian.gitlabplus.model.ProjectSelection;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 设置页：GitLab 服务器、Access Token、默认项目全路径。
 */
public final class GitLabPlusConfigurable implements Configurable {

  private JTextField serverField;
  private JPasswordField tokenField;
  private JTextField projectPathField;
  private JButton importButton;
  private JButton clearTokenButton;
  private JCheckBox checkoutTargetCheck;
  private JCheckBox deleteSourceCheck;
  private JTextArea titleTemplateArea;
  private JTextArea descriptionTemplateArea;
  private JButton resetTemplatesButton;
  private JTextField watchTimeoutField;
  private JComboBox<String> defaultStateCombo;
  private JComboBox<String> languageCombo;
  private JCheckBox defaultRemoveSourceCheck;
  private JCheckBox defaultSquashCheck;
  private DefaultListModel<String> remoteProjectModel;
  private DefaultListModel<String> dropdownProjectModel;
  @Override
  public String getDisplayName() {
    return "GitLab+ Merge Tool";
  }

  @Override
  public @Nullable String getHelpTopic() {
    return null;
  }

  @Override
  public @Nullable JComponent createComponent() {
    serverField = new JTextField(32);
    tokenField = new JPasswordField(32);
    projectPathField = new JTextField(32);
    importButton = new JButton(GitLabPlusBundle.message("settings.importToken"));
    importButton.addActionListener(e -> importFromGitLabPlugin());
    clearTokenButton = new JButton(GitLabPlusBundle.message("settings.clearToken"));
    clearTokenButton.setToolTipText(GitLabPlusBundle.message("settings.clearToken"));
    clearTokenButton.addActionListener(e -> clearToken());
    checkoutTargetCheck = new JCheckBox(GitLabPlusBundle.message("settings.checkoutAfterMerge"));
    deleteSourceCheck = new JCheckBox(GitLabPlusBundle.message("settings.deleteSourceAfterMerge"));
    titleTemplateArea = new JTextArea(2, 52);
    descriptionTemplateArea = new JTextArea(6, 52);
    titleTemplateArea.setLineWrap(true);
    titleTemplateArea.setWrapStyleWord(true);
    descriptionTemplateArea.setLineWrap(true);
    descriptionTemplateArea.setWrapStyleWord(true);
    resetTemplatesButton = new JButton(GitLabPlusBundle.message("settings.resetTemplates"));
    resetTemplatesButton.addActionListener(e -> {
      titleTemplateArea.setText("");
      descriptionTemplateArea.setText("");
    });

    watchTimeoutField = new JTextField(6);
    defaultStateCombo = new JComboBox<>(new String[]{"all", "opened", "merged", "closed"});
    defaultRemoveSourceCheck = new JCheckBox(GitLabPlusBundle.message("settings.defaultRemoveSource"));
    defaultSquashCheck = new JCheckBox(GitLabPlusBundle.message("settings.defaultSquash"));
    languageCombo = new JComboBox<>(new String[]{
        GitLabPlusBundle.message("settings.languageAuto"),
        GitLabPlusBundle.message("settings.languageZh"),
        GitLabPlusBundle.message("settings.languageEn")});

    JPanel panel = FormBuilder.createFormBuilder()
        .addLabeledComponent(new JLabel(GitLabPlusBundle.message("settings.server")), serverField, 1, false)
        .addLabeledComponent(new JLabel(GitLabPlusBundle.message("settings.token")), tokenField, 1, false)
        .addLabeledComponent(new JLabel(GitLabPlusBundle.message("settings.projectPath")), projectPathField, 1, false)
        .addComponent(importButton, 1)
        .addComponent(clearTokenButton, 1)
        .addComponent(new JLabel(GitLabPlusBundle.message("settings.projectPathHint")))
        .addSeparator(10)
        .addComponent(new JLabel(GitLabPlusBundle.message("settings.localGitHint")))
        .addComponent(checkoutTargetCheck, 1)
        .addComponent(deleteSourceCheck, 1)
        .addSeparator(10)
        .addComponent(new JLabel(GitLabPlusBundle.message("settings.templates")))
        .addComponent(new JLabel(GitLabPlusBundle.message("settings.placeholders")))
        .addComponent(new JScrollPane(titleTemplateArea), 1)
        .addComponent(new JLabel(GitLabPlusBundle.message("settings.descriptionTemplate")))
        .addComponent(new JScrollPane(descriptionTemplateArea), 1)
        .addComponent(resetTemplatesButton, 1)
        .addComponent(new JLabel(GitLabPlusBundle.message("settings.mrDefaults")))
        .addLabeledComponent(new JLabel(GitLabPlusBundle.message("settings.defaultState")), defaultStateCombo, 1, false)
        .addComponent(defaultRemoveSourceCheck, 1)
        .addComponent(defaultSquashCheck, 1)
        .addSeparator(10)
        .addComponent(new JLabel(GitLabPlusBundle.message("settings.projects")))
        .addComponent(buildProjectSelector(), 1)
        .addSeparator(10)
        .addLabeledComponent(new JLabel(GitLabPlusBundle.message("settings.watchTimeout")), watchTimeoutField, 1, false)
        .addComponent(new JLabel(GitLabPlusBundle.message("settings.watchHint")))
        .addSeparator(10)
        .addLabeledComponent(new JLabel(GitLabPlusBundle.message("settings.language")), languageCombo, 1, false)
        .addComponent(new JLabel(GitLabPlusBundle.message("settings.languageHint")))
        .addComponentFillVertically(new JPanel(), 0)
        .getPanel();
    reset();
    return panel;
  }

  /**
   * 从内置 GitLab 插件已登录的账户导入 PAT：
   * 账户元数据读 {@code options/gitlab.xml}，凭据经 PasswordSafe 由平台解密。
   */
  private void importFromGitLabPlugin() {
    List<OfficialGitLabTokenImporter.ImportedAccount> accounts = OfficialGitLabTokenImporter.importAccounts();
    if (accounts.isEmpty()) {
      Messages.showInfoMessage(
          "未找到内置 GitLab 插件的账户或凭据。\n请先在内置 GitLab 插件里登录，或手动填写 Token。",
          "GitLab+ 导入 Token");
      return;
    }
    String currentServer = serverField.getText() == null ? "" : serverField.getText().trim();
    OfficialGitLabTokenImporter.ImportedAccount chosen = accounts.get(0);
    for (OfficialGitLabTokenImporter.ImportedAccount account : accounts) {
      String accountServer = account.serverUrl() == null ? "" : account.serverUrl().trim();
      if (!currentServer.isEmpty() && accountServer.equalsIgnoreCase(currentServer)) {
        chosen = account;
        break;
      }
    }
    serverField.setText(chosen.serverUrl());
    tokenField.setText(chosen.token());
    Messages.showInfoMessage(
        "已导入账户：" + chosen.accountName() + "（" + chosen.serverUrl() + "）\n"
            + "请点 Apply 保存后再刷新列表。",
        "GitLab+ 导入 Token");
  }

  /** 语言下拉的当前值（auto / zh / en）。 */
  private String languageValue() {
    if (languageCombo == null) {
      return LanguageService.AUTO;
    }
    return switch (languageCombo.getSelectedIndex()) {
      case 1 -> LanguageService.ZH;
      case 2 -> LanguageService.EN;
      default -> LanguageService.AUTO;
    };
  }

  private void resetLanguageCombo() {
    if (languageCombo == null) {
      return;
    }
    String language = LanguageService.getInstance().getLanguage();
    languageCombo.setSelectedIndex(switch (language) {
      case LanguageService.ZH -> 1;
      case LanguageService.EN -> 2;
      default -> 0;
    });
  }

  /** 从 IDE 凭据库删除当前服务器的 Token。 */
  private void clearToken() {
    String server = serverField.getText().trim();
    if (server.isEmpty()) {
      Messages.showWarningDialog(GitLabPlusBundle.message("settings.clearTokenNeedServer"),
                                 GitLabPlusBundle.message("settings.clearTokenTitle"));
      return;
    }
    if (Messages.showYesNoDialog(GitLabPlusBundle.message("settings.clearTokenConfirm", server),
                                 GitLabPlusBundle.message("settings.clearTokenTitle"),
                                 Messages.getWarningIcon()) != Messages.YES) {
      return;
    }
    GitLabTokenStore.setToken(server, null);
    tokenField.setText("");
    Messages.showInfoMessage(GitLabPlusBundle.message("settings.clearTokenDone", server),
                             GitLabPlusBundle.message("settings.clearTokenTitle"));
  }

  @Override
  public boolean isModified() {
    GitLabPlusSettings settings = GitLabPlusSettings.getInstance();
    return !Objects.equals(serverField.getText().trim(), settings.getServerUrl())
        || !Objects.equals(projectPathField.getText().trim(), settings.getProjectPath())
        || !Objects.equals(new String(tokenField.getPassword()), nullToEmpty(settings.getToken()))
        || checkoutTargetCheck.isSelected() != settings.isCheckoutTargetAfterMerge()
        || deleteSourceCheck.isSelected() != settings.isDeleteLocalSourceAfterMerge()
        || !Objects.equals(titleTemplateArea.getText(), nullToEmpty(settings.getTitleTemplateRaw()))
        || !Objects.equals(descriptionTemplateArea.getText(), nullToEmpty(settings.getDescriptionTemplateRaw()))
        || !Objects.equals(watchTimeoutField.getText().trim(), String.valueOf(settings.getPipelineWatchTimeoutMinutes()))
        || !Objects.equals(selected(defaultStateCombo), settings.getDefaultMrState())
        || defaultRemoveSourceCheck.isSelected() != settings.isDefaultRemoveSourceBranch()
        || defaultSquashCheck.isSelected() != settings.isDefaultSquashOnMerge()
        || !dropdownValues().equals(settings.getDropdownProjects())
        || !Objects.equals(languageValue(), LanguageService.getInstance().getLanguage());
  }

  @Override
  public void apply() {
    GitLabPlusSettings settings = GitLabPlusSettings.getInstance();
    String server = serverField.getText().trim();
    if (server.isEmpty()) {
      // Token 按服务器地址做 key，地址为空时 setToken 会静默丢弃：显式告知而不是默默不保存
      Messages.showWarningDialog(GitLabPlusBundle.message("settings.emptyServerWarn"),
                                 GitLabPlusBundle.message("settings.displayName"));
    }
    settings.setServerUrl(server);
    settings.setProjectPath(projectPathField.getText().trim());
    settings.setToken(new String(tokenField.getPassword()));
    settings.setCheckoutTargetAfterMerge(checkoutTargetCheck.isSelected());
    settings.setDeleteLocalSourceAfterMerge(deleteSourceCheck.isSelected());
    settings.setTitleTemplate(titleTemplateArea.getText().isBlank() ? null : titleTemplateArea.getText());
    settings.setDescriptionTemplate(
        descriptionTemplateArea.getText().isBlank() ? null : descriptionTemplateArea.getText());
    settings.setPipelineWatchTimeoutMinutes(parsePositive(watchTimeoutField.getText(), 30));
    settings.setDefaultMrState(selected(defaultStateCombo));
    settings.setDefaultRemoveSourceBranch(defaultRemoveSourceCheck.isSelected());
    settings.setDefaultSquashOnMerge(defaultSquashCheck.isSelected());
    settings.setDropdownProjects(dropdownValues());
    String previousLanguage = LanguageService.getInstance().getLanguage();
    LanguageService.getInstance().setLanguage(languageValue());
    // 通知工具窗口：重建 client 并重载（否则用户会以为「改了不起作用」）
    SettingsChangedNotifier.fireChanged();
    if (!previousLanguage.equals(LanguageService.getInstance().getLanguage())) {
      Messages.showInfoMessage(GitLabPlusBundle.message("settings.languageRestart"),
                               GitLabPlusBundle.message("settings.language"));
    }
  }

  @Override
  public void reset() {
    GitLabPlusSettings settings = GitLabPlusSettings.getInstance();
    if (settings.getServerUrl() != null) {
      serverField.setText(settings.getServerUrl());
    }
    if (settings.getProjectPath() != null) {
      projectPathField.setText(settings.getProjectPath());
    }
    tokenField.setText(nullToEmpty(settings.getToken()));
    checkoutTargetCheck.setSelected(settings.isCheckoutTargetAfterMerge());
    deleteSourceCheck.setSelected(settings.isDeleteLocalSourceAfterMerge());
    titleTemplateArea.setText(nullToEmpty(settings.getTitleTemplateRaw()));
    descriptionTemplateArea.setText(nullToEmpty(settings.getDescriptionTemplateRaw()));
    watchTimeoutField.setText(String.valueOf(settings.getPipelineWatchTimeoutMinutes()));
    defaultStateCombo.setSelectedItem(settings.getDefaultMrState());
    defaultRemoveSourceCheck.setSelected(settings.isDefaultRemoveSourceBranch());
    defaultSquashCheck.setSelected(settings.isDefaultSquashOnMerge());
    resetProjectLists();
    resetLanguageCombo();
  }

  @Override
  public void disposeUIResources() {
    serverField = null;
    tokenField = null;
    projectPathField = null;
    importButton = null;
    clearTokenButton = null;
    checkoutTargetCheck = null;
    deleteSourceCheck = null;
    titleTemplateArea = null;
    descriptionTemplateArea = null;
    resetTemplatesButton = null;
    watchTimeoutField = null;
    defaultStateCombo = null;
    defaultRemoveSourceCheck = null;
    defaultSquashCheck = null;
    languageCombo = null;
    remoteProjectModel = null;
    dropdownProjectModel = null;
  }

  // ------------------------------------------------------------------ 项目下拉的双列表

  /**
   * 左 = 可用项目（IDEA 中所有 Git 仓库的 remote），右 = 下拉中显示的项目。
   *
   * <p>→ 加入下拉；← 移回可用；↑↓ 调整下拉顺序（顺序即工具窗口下拉的顺序）。</p>
   */
  private JComponent buildProjectSelector() {
    remoteProjectModel = new DefaultListModel<>();
    dropdownProjectModel = new DefaultListModel<>();
    JList<String> remoteList = new JList<>(remoteProjectModel);
    JList<String> dropdownList = new JList<>(dropdownProjectModel);
    for (JList<String> list : List.of(remoteList, dropdownList)) {
      list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      list.setVisibleRowCount(8);
    }

    JButton toDropdown = new JButton("→");
    JButton toRemote = new JButton("←");
    JButton up = new JButton("↑");
    JButton down = new JButton("↓");
    JButton refresh = new JButton(GitLabPlusBundle.message("settings.refreshRemote"));
    JButton fillAll = new JButton(GitLabPlusBundle.message("settings.moveAll"));

    toDropdown.setToolTipText("把选中的可用项目加入下拉列表");
    toRemote.setToolTipText("从下拉列表移回可用项目");
    up.setToolTipText("上移（影响工具窗口下拉的先后）");
    down.setToolTipText("下移（影响工具窗口下拉的先后）");
    refresh.setToolTipText("重新扫描 IDEA 中的 Git 仓库，新项目放入左侧可用列表");
    fillAll.setToolTipText("把所有可用项目放入下拉列表");

    toDropdown.addActionListener(e -> moveSelected(remoteList, remoteProjectModel,
                                                   dropdownProjectModel, dropdownProjectModel.size()));
    toRemote.addActionListener(e -> moveSelected(dropdownList, dropdownProjectModel,
                                                 remoteProjectModel, remoteProjectModel.size()));
    up.addActionListener(e -> move(dropdownList, dropdownProjectModel, -1));
    down.addActionListener(e -> move(dropdownList, dropdownProjectModel, 1));
    refresh.addActionListener(e -> refreshRemoteProjects());
    fillAll.addActionListener(e -> moveAllToDropdown());

    // 左右按钮固定尺寸，不随主窗体拉伸（之前用 GridLayout 被拉成了“胖子”）
    java.awt.Dimension arrowSize = new java.awt.Dimension(36, 28);
    for (JButton button : List.of(toDropdown, toRemote)) {
      button.setPreferredSize(arrowSize);
      button.setMinimumSize(arrowSize);
      button.setMaximumSize(arrowSize);
      button.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
    }
    JPanel arrows = new JPanel();
    arrows.setLayout(new javax.swing.BoxLayout(arrows, javax.swing.BoxLayout.Y_AXIS));
    arrows.add(javax.swing.Box.createVerticalGlue());
    arrows.add(toDropdown);
    arrows.add(javax.swing.Box.createVerticalStrut(4));
    arrows.add(toRemote);
    arrows.add(javax.swing.Box.createVerticalGlue());
    // 中间列固定宽度：不参与水平拉伸，左右列表吸收多余宽度
    arrows.setPreferredSize(new java.awt.Dimension(44, 0));
    arrows.setMinimumSize(new java.awt.Dimension(44, 0));

    // 上下排序按钮：同样固定尺寸，避免被拉宽拉高
    java.awt.Dimension orderSize = new java.awt.Dimension(30, 26);
    for (JButton button : List.of(up, down)) {
      button.setPreferredSize(orderSize);
      button.setMinimumSize(orderSize);
      button.setMaximumSize(orderSize);
      button.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
    }
    JPanel order = new JPanel();
    order.setLayout(new javax.swing.BoxLayout(order, javax.swing.BoxLayout.Y_AXIS));
    order.add(javax.swing.Box.createVerticalStrut(2));
    order.add(up);
    order.add(javax.swing.Box.createVerticalStrut(4));
    order.add(down);
    order.add(javax.swing.Box.createVerticalGlue());
    order.setPreferredSize(new java.awt.Dimension(32, 0));

    JPanel left = new JPanel(new BorderLayout(0, 2));
    left.add(new JLabel(GitLabPlusBundle.message("settings.remoteList")), BorderLayout.NORTH);
    left.add(new JBScrollPane(remoteList), BorderLayout.CENTER);

    JPanel rightBody = new JPanel(new BorderLayout(4, 0));
    rightBody.add(new JBScrollPane(dropdownList), BorderLayout.CENTER);
    rightBody.add(order, BorderLayout.EAST);
    JPanel right = new JPanel(new BorderLayout(0, 2));
    right.add(new JLabel(GitLabPlusBundle.message("settings.dropdownList")), BorderLayout.NORTH);
    right.add(rightBody, BorderLayout.CENTER);

    // GridBagLayout：左右列表 weightx=1 吸收拉伸，中间按钮列 weightx=0 间距固定
    JPanel columns = new JPanel(new GridBagLayout());
    java.awt.GridBagConstraints constraints = new java.awt.GridBagConstraints();
    constraints.fill = java.awt.GridBagConstraints.BOTH;
    constraints.weighty = 1.0;

    constraints.gridx = 0;
    constraints.weightx = 1.0;
    constraints.insets = new java.awt.Insets(0, 0, 0, 6);
    columns.add(left, constraints);

    constraints.gridx = 1;
    constraints.weightx = 0.0;
    constraints.insets = new java.awt.Insets(0, 0, 0, 6);
    columns.add(arrows, constraints);

    constraints.gridx = 2;
    constraints.weightx = 1.0;
    constraints.insets = new java.awt.Insets(0, 0, 0, 0);
    columns.add(right, constraints);

    columns.setPreferredSize(new java.awt.Dimension(660, 200));

    JPanel tools = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 2));
    tools.add(refresh);
    tools.add(fillAll);

    JPanel panel = new JPanel(new BorderLayout(0, 4));
    panel.add(columns, BorderLayout.CENTER);
    panel.add(tools, BorderLayout.SOUTH);
    return panel;
  }

  /** 下拉列表当前内容（顺序即顺序）。 */
  private List<String> dropdownValues() {
    List<String> values = new ArrayList<>();
    if (dropdownProjectModel != null) {
      for (int i = 0; i < dropdownProjectModel.size(); i++) {
        values.add(dropdownProjectModel.elementAt(i));
      }
    }
    return values;
  }

  /** 按设置重建两个列表：首次（未配置）时下拉列表 = 全部项目。 */
  private void resetProjectLists() {
    if (remoteProjectModel == null || dropdownProjectModel == null) {
      return;
    }
    remoteProjectModel.clear();
    dropdownProjectModel.clear();
    List<String> available = allRemoteProjects();
    List<String> ordered = ProjectSelection.ordered(
        GitLabPlusSettings.getInstance().getDropdownProjects(), available);
    for (String path : ordered) {
      dropdownProjectModel.addElement(path);
    }
    Set<String> inDropdown = new LinkedHashSet<>(ordered);
    for (String path : available) {
      if (!inDropdown.contains(path)) {
        remoteProjectModel.addElement(path);
      }
    }
  }

  private void refreshRemoteProjects() {
    if (remoteProjectModel == null || dropdownProjectModel == null) {
      return;
    }
    Set<String> known = new LinkedHashSet<>(dropdownValues());
    for (int i = 0; i < remoteProjectModel.size(); i++) {
      known.add(remoteProjectModel.elementAt(i));
    }
    int added = 0;
    for (String path : allRemoteProjects()) {
      if (known.add(path)) {
        remoteProjectModel.addElement(path);
        added++;
      }
    }
    Messages.showInfoMessage(GitLabPlusBundle.message("settings.refreshDone", added),
                             GitLabPlusBundle.message("settings.refreshTitle"));
  }

  private void moveAllToDropdown() {
    if (remoteProjectModel == null || dropdownProjectModel == null) {
      return;
    }
    for (int i = 0; i < remoteProjectModel.size(); i++) {
      String value = remoteProjectModel.elementAt(i);
      if (!containsValue(dropdownProjectModel, value)) {
        dropdownProjectModel.addElement(value);
      }
    }
    remoteProjectModel.clear();
  }

  private static void moveSelected(JList<String> sourceList, DefaultListModel<String> sourceModel,
                                   DefaultListModel<String> targetModel, int insertAt) {
    List<String> selected = sourceList.getSelectedValuesList();
    if (selected.isEmpty()) {
      return;
    }
    int index = Math.max(0, Math.min(insertAt, targetModel.size()));
    for (String value : selected) {
      if (!containsValue(targetModel, value)) {
        targetModel.add(index++, value);
      }
    }
    for (String value : selected) {
      sourceModel.removeElement(value);
    }
    sourceList.clearSelection();
  }

  private static void move(JList<String> list, DefaultListModel<String> model, int delta) {
    int index = list.getSelectedIndex();
    int target = index + delta;
    if (index < 0 || target < 0 || target >= model.size()) {
      return;
    }
    String value = model.elementAt(index);
    model.set(index, model.elementAt(target));
    model.set(target, value);
    list.setSelectedIndex(target);
    list.ensureIndexIsVisible(target);
  }

  private static boolean containsValue(DefaultListModel<String> model, String value) {
    for (int i = 0; i < model.size(); i++) {
      if (model.elementAt(i).equals(value)) {
        return true;
      }
    }
    return false;
  }

  /** IDEA 中所有 Git 仓库 remote 解析出的项目全路径（去重排序）。 */
  private static List<String> allRemoteProjects() {
    Set<String> paths = new LinkedHashSet<>();
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      for (GitProjectResolver.Resolved resolved : GitProjectResolver.resolveAll(project)) {
        paths.add(resolved.projectPath());
      }
    }
    List<String> result = new ArrayList<>(paths);
    result.sort(String::compareToIgnoreCase);
    return result;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  /** 取下拉当前值（为空时返回空串）。 */
  private static String selected(JComboBox<String> combo) {
    Object value = combo.getSelectedItem();
    return value == null ? "" : value.toString();
  }

  private static int parsePositive(String text, int fallback) {
    try {
      int value = Integer.parseInt(text == null ? "" : text.trim());
      return value > 0 ? value : fallback;
    }
    catch (NumberFormatException e) {
      return fallback;
    }
  }
}
