package com.hakil.gitlabplus.ui;

import com.hakil.gitlabplus.i18n.GitLabPlusBundle;
import com.hakil.gitlabplus.model.ProjectMember;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 审阅人选择器：从项目成员里多选，而不是让用户手输用户名。
 *
 * <p>手输的实际问题：用户名记不住、拼错会被 GitLab 忽略或整批失败，
 * 而且看不到项目里到底有哪些人可选。</p>
 *
 * <p>过滤只影响视图：已选中但被过滤隐藏的成员仍会保留在结果里
 * （用独立的 {@code chosen} 集合跟踪，而不是读列表的选中项）。</p>
 */
public final class ReviewerPickerDialog extends DialogWrapper {

  private final List<ProjectMember> allMembers;
  private final DefaultListModel<ProjectMember> model = new DefaultListModel<>();
  private final JList<ProjectMember> list = new JList<>(model);
  private final JTextField filterField = new JTextField();
  /** 真正的选择结果（与列表可见项解耦）。 */
  private final Set<String> chosen = new LinkedHashSet<>();
  private boolean refilling;

  public ReviewerPickerDialog(Project project, List<ProjectMember> members,
                              Collection<String> currentReviewers) {
    super(project);
    this.allMembers = members == null ? List.of() : members;
    if (currentReviewers != null) {
      chosen.addAll(currentReviewers);
    }
    setTitle(GitLabPlusBundle.message("reviewers.title"));
    init();
    refill("");
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    list.setVisibleRowCount(14);
    list.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting() && !refilling) {
        syncChosen();
      }
    });

    JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
    filterRow.add(new JLabel(GitLabPlusBundle.message("reviewers.filter")));
    filterField.setColumns(24);
    filterRow.add(filterField);
    filterField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        refill(filterField.getText());
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        refill(filterField.getText());
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        refill(filterField.getText());
      }
    });

    JPanel panel = new JPanel(new BorderLayout(0, 4));
    panel.add(filterRow, BorderLayout.NORTH);
    panel.add(new JBScrollPane(list), BorderLayout.CENTER);
    panel.add(new JLabel(GitLabPlusBundle.message("reviewers.hint")), BorderLayout.SOUTH);
    panel.setPreferredSize(new Dimension(430, 360));
    panel.setMinimumSize(new Dimension(280, 200));
    return panel;
  }

  /** 按关键字重建列表；已选中的可见项保持选中。 */
  private void refill(String keyword) {
    String needle = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
    refilling = true;
    try {
      model.clear();
      List<Integer> toSelect = new ArrayList<>();
      for (ProjectMember member : allMembers) {
        if (member == null || member.username() == null || member.username().isBlank()) {
          continue;
        }
        String haystack = (member.username() + " " + (member.name() == null ? "" : member.name()))
            .toLowerCase(Locale.ROOT);
        if (!needle.isEmpty() && !haystack.contains(needle)) {
          continue;
        }
        if (chosen.contains(member.username())) {
          toSelect.add(model.size());
        }
        model.addElement(member);
      }
      list.setSelectedIndices(toSelect.stream().mapToInt(Integer::intValue).toArray());
    }
    finally {
      refilling = false;
    }
  }

  /** 把列表可见项的选中状态合并进 {@code chosen}，隐藏项原样保留。 */
  private void syncChosen() {
    Set<String> visible = new HashSet<>();
    for (int i = 0; i < model.size(); i++) {
      visible.add(model.get(i).username());
    }
    chosen.removeAll(visible);
    for (ProjectMember member : list.getSelectedValuesList()) {
      if (member != null) {
        chosen.add(member.username());
      }
    }
  }

  /** 当前选中的用户名（按项目成员原始顺序，避免顺序抖动）。 */
  public List<String> selectedUsernames() {
    syncChosen();
    List<String> ordered = new ArrayList<>();
    for (ProjectMember member : allMembers) {
      if (member != null && chosen.contains(member.username())) {
        ordered.add(member.username());
      }
    }
    // 兜底：projectPath 之外传入的用户名（例如已离组成员）也保留，避免静默丢失
    for (String username : chosen) {
      if (!ordered.contains(username)) {
        ordered.add(username);
      }
    }
    return ordered;
  }
}
