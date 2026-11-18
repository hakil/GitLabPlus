package com.hakil.gitlabplus.ui;

import com.hakil.gitlabplus.git.BranchLister;
import com.hakil.gitlabplus.i18n.GitLabPlusBundle;
import com.hakil.gitlabplus.git.BranchPusher;
import com.hakil.gitlabplus.model.BranchOrdering;
import com.hakil.gitlabplus.model.TemplateRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Dimension;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 创建 merge request 对话框（对齐官方 create MR 的字段集）。
 *
 * <p>源 / 目标分支为可编辑下拉，并<b>区分本地与远程</b>：
 * 显示为 <code>[本地·已推送]</code> / <code>[本地·未推送]</code> / <code>[仅远程]</code>。</p>
 *
 * <p>选中「本地·未推送」分支时会在对话框内提示：创建前需先推送，
 * 而推送被拒绝（远程有新提交）时会停止并要求先 Pull 解决冲突。</p>
 */
public final class CreateMergeRequestDialog extends DialogWrapper {

  /** 下拉项：分支名 + 来源标识。 */
  private record BranchItem(String name, boolean local, boolean pushed) {
    @Override
    public String toString() {
      String scope = local ? (pushed ? "本地·已推送" : "本地·未推送") : "仅远程";
      return "[" + scope + "] " + name;
    }
  }

  private final JComboBox<BranchItem> sourceCombo = new JComboBox<>();
  private final JComboBox<BranchItem> targetCombo = new JComboBox<>();
  private final JTextField titleField = new JTextField(32);
  private final JTextArea descriptionArea = new JTextArea(6, 40);
  private final JLabel hintLabel = new JLabel(" ");
  private final JCheckBox removeSourceCheck = new JCheckBox(GitLabPlusBundle.message("create.removeSource"), true);
  private final JCheckBox squashCheck = new JCheckBox(GitLabPlusBundle.message("create.squash"), false);

  private final String titleTemplate;
  private final Map<String, String> variables;
  private final File workDir;
  /** 用户是否仍在使用自动生成的标题（手改后不再跟随目标分支）。 */
  private boolean titleAutoFilled = true;
  private boolean updatingTitle = false;

  public CreateMergeRequestDialog(Project project,
                                  BranchLister.Branches branches,
                                  File workDir,
                                  String currentBranch,
                                  String defaultTargetBranch,
                                  String titleTemplate,
                                  String descriptionTemplate,
                                  Map<String, String> variables,
                                  boolean defaultRemoveSource,
                                  boolean defaultSquash) {
    super(project, true);
    this.workDir = workDir;
    removeSourceCheck.setSelected(defaultRemoveSource);
    squashCheck.setSelected(defaultSquash);
    this.titleTemplate = titleTemplate == null ? "" : titleTemplate;
    this.variables = variables == null ? Map.of() : variables;
    fillBranches(branches, currentBranch, defaultTargetBranch);
    descriptionArea.setText(TemplateRenderer.render(descriptionTemplate, this.variables));
    descriptionArea.setCaretPosition(0);
    descriptionArea.setLineWrap(true);
    descriptionArea.setWrapStyleWord(true);
    titleField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        markManual();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        markManual();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        markManual();
      }
    });
    setTitle(GitLabPlusBundle.message("create.title"));
    init();
    updateHint();
    refreshAutoTitle();
  }

  /** 用户手动编辑标题后，就不再随目标分支重算。 */
  private void markManual() {
    if (!updatingTitle) {
      titleAutoFilled = false;
    }
  }

  /** 根据当前源/目标分支重新渲染标题（仅当用户未手改）。 */
  private void refreshAutoTitle() {
    if (!titleAutoFilled) {
      return;
    }
    Map<String, String> vars = new LinkedHashMap<>(variables);
    vars.put("source", sourceBranch());
    vars.put("target", targetBranch());
    vars.put("branchTitle", TemplateRenderer.branchTitle(sourceBranch()));
    updatingTitle = true;
    try {
      titleField.setText(TemplateRenderer.render(titleTemplate, vars).trim());
      titleField.setCaretPosition(0);
    }
    finally {
      updatingTitle = false;
    }
  }

  /**
   * 列宽由 {@link CreateMergeRequestForm} 统一拉伸，这里只给下拉一个初始宽度。
   *
   * <p>不为标题框设 {@code setPreferredSize}：文本组件的宽度由列数决定，显式尺寸不生效，
   * 反而会让它比下拉窄一截。</p>
   */
  private void fillBranches(BranchLister.Branches branches,
                            String currentBranch,
                            String defaultTarget) {
    sourceCombo.setEditable(true);
    targetCombo.setEditable(true);
    int fieldWidth = 560;
    sourceCombo.setPreferredSize(new Dimension(fieldWidth, sourceCombo.getPreferredSize().height));
    targetCombo.setPreferredSize(new Dimension(fieldWidth, targetCombo.getPreferredSize().height));
    sourceCombo.setMinimumSize(new Dimension(320, sourceCombo.getPreferredSize().height));
    targetCombo.setMinimumSize(new Dimension(320, targetCombo.getPreferredSize().height));

    List<String> orderedTargets = List.of();
    if (branches != null) {
      for (String name : BranchOrdering.orderSources(branches.sourceCandidates(), currentBranch)) {
        sourceCombo.addItem(new BranchItem(name, branches.isLocal(name), branches.isPushed(name)));
      }
      // 目标分支：场景推荐 → 上次使用 → 主干优先级 → 其余，减少手动选择
      orderedTargets =
          BranchOrdering.orderTargets(branches.targetCandidates(), defaultTarget, currentBranch);
      for (String name : orderedTargets) {
        targetCombo.addItem(new BranchItem(name, branches.isLocal(name), branches.isPushed(name)));
      }
    }

    selectByBranchName(sourceCombo, currentBranch);
    selectByBranchName(targetCombo,
                       BranchOrdering.pickTarget(orderedTargets, defaultTarget, currentBranch));

    // 让弹出层按最长项估算宽度，避免长分支名被截断
    applyPrototype(sourceCombo);
    applyPrototype(targetCombo);

    sourceCombo.addActionListener(e -> {
      updateHint();
      refreshAutoTitle();
    });
    targetCombo.addActionListener(e -> {
      updateHint();
      refreshAutoTitle();
    });
  }

  private static void applyPrototype(JComboBox<BranchItem> combo) {
    BranchItem longest = null;
    for (int i = 0; i < combo.getItemCount(); i++) {
      BranchItem item = combo.getItemAt(i);
      if (item == null) {
        continue;
      }
      if (longest == null || item.toString().length() > longest.toString().length()) {
        longest = item;
      }
    }
    if (longest != null) {
      combo.setPrototypeDisplayValue(longest);
    }
  }

  private void selectByBranchName(JComboBox<BranchItem> combo, String branchName) {
    if (branchName != null && !branchName.isBlank()) {
      for (int i = 0; i < combo.getItemCount(); i++) {
        BranchItem item = combo.getItemAt(i);
        if (item != null && item.name().equals(branchName)) {
          combo.setSelectedIndex(i);
          return;
        }
      }
      combo.getEditor().setItem(branchName);
      return;
    }
    if (combo.getItemCount() > 0) {
      combo.setSelectedIndex(0);
    }
  }

  private void updateHint() {
    BranchItem source = selectedItem(sourceCombo);
    if (source == null) {
      hintLabel.setText(" ");
      return;
    }
    if (source.local() && !source.pushed()) {
      hintLabel.setText("<html>该分支尚未推送到远程：创建前会自动 <code>git push -u origin "
                            + escape(source.name())
                            + "</code>；若远程已有新提交导致推送被拒绝，会停下并要求先 Pull 解决冲突。</html>");
    }
    else if (source.local()) {
      // 远程已存在同名分支，但本地可能还有未推送提交（含该情况的 MR 会没有任何变更）
      int ahead = workDir == null ? -1 : BranchPusher.aheadCount(workDir, source.name());
      if (ahead > 0) {
        hintLabel.setText("<html>该分支有 <b>" + ahead + "</b> 个提交尚未推送：创建前会自动 push，否则 MR 不会有任何变更。"
                              + "若推送被拒绝，会停下并要求先 Pull 解决冲突。</html>");
      }
      else {
        hintLabel.setText("<html>该分支与远程 <code>origin/" + escape(source.name()) + "</code> 已同步。</html>");
      }
    }
    else if (!source.local()) {
      hintLabel.setText("<html>该分支仅存在于远程，将直接以远程分支创建 merge request。</html>");
    }
    else {
      hintLabel.setText(" ");
    }
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    JPanel panel = CreateMergeRequestForm.build(List.of(
        new CreateMergeRequestForm.Row(GitLabPlusBundle.message("create.source") + "：", sourceCombo, false),
        new CreateMergeRequestForm.Row(GitLabPlusBundle.message("create.target") + "：", targetCombo, false),
        new CreateMergeRequestForm.Row(null, hintLabel, true),
        new CreateMergeRequestForm.Row(GitLabPlusBundle.message("create.titleField") + "：", titleField, false),
        new CreateMergeRequestForm.Row(GitLabPlusBundle.message("create.descriptionField") + "：",
                                       new JScrollPane(descriptionArea), false),
        new CreateMergeRequestForm.Row(null, removeSourceCheck, true),
        new CreateMergeRequestForm.Row(null, squashCheck, true)));
    panel.setMinimumSize(CreateMergeRequestForm.minimumSize());
    panel.setPreferredSize(new Dimension(760, 460));
    return panel;
  }

  @Override
  protected @Nullable ValidationInfo doValidate() {
    if (sourceBranch().isBlank()) {
      return new ValidationInfo("源分支不能为空", sourceCombo);
    }
    if (targetBranch().isBlank()) {
      return new ValidationInfo("目标分支不能为空", targetCombo);
    }
    if (sourceBranch().equals(targetBranch())) {
      return new ValidationInfo("源分支与目标分支不能相同", targetCombo);
    }
    if (titleField.getText().isBlank()) {
      return new ValidationInfo("标题不能为空", titleField);
    }
    return null;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return targetCombo;
  }

  public String sourceBranch() {
    return comboValue(sourceCombo);
  }

  public String targetBranch() {
    return comboValue(targetCombo);
  }

  public String title() {
    return titleField.getText().trim();
  }

  public String description() {
    return descriptionArea.getText();
  }

  public boolean removeSourceBranch() {
    return removeSourceCheck.isSelected();
  }

  public boolean squash() {
    return squashCheck.isSelected();
  }

  private static BranchItem selectedItem(JComboBox<BranchItem> combo) {
    Object value = combo.getSelectedItem();
    return value instanceof BranchItem item ? item : null;
  }

  /** 可编辑下拉：编辑器文本与选中项一致时取分支名；否则视为手输。 */
  private static String comboValue(JComboBox<BranchItem> combo) {
    Object selected = combo.getSelectedItem();
    Object edited = combo.isEditable() ? combo.getEditor().getItem() : null;
    String editedText = edited == null ? "" : edited.toString().trim();
    if (!editedText.isEmpty()) {
      if (selected instanceof BranchItem item && editedText.equals(item.toString())) {
        return item.name();
      }
      if (!editedText.startsWith("[")) {
        return editedText;
      }
      return "";
    }
    return selected instanceof BranchItem item ? item.name() : "";
  }

  private static String escape(String text) {
    return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
