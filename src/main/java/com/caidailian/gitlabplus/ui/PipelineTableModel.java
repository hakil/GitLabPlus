package com.caidailian.gitlabplus.ui;

import com.caidailian.gitlabplus.model.PipelineInfo;
import com.caidailian.gitlabplus.model.PipelineRole;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * CI 页签上表（流水线列表）的模型。
 *
 * <p>从 CiPanel 抽出来：面板只关心选中与操作，行列映射与角色文案属于纯展示逻辑。</p>
 */
public final class PipelineTableModel extends AbstractTableModel {

  private final String[] columns = {"#", "状态", "触发", "来源", "SHA", "创建时间"};
  private final List<PipelineInfo> rows = new ArrayList<>();
  private String sourceBranch = "";
  private String targetBranch = "";
  private String mergeSha = "";

  /** 角色判定（合并结果 / MR 校验 / 推送…）依赖的 MR 上下文。 */
  public void setContext(String sourceBranch, String targetBranch, String mergeSha) {
    this.sourceBranch = sourceBranch == null ? "" : sourceBranch;
    this.targetBranch = targetBranch == null ? "" : targetBranch;
    this.mergeSha = mergeSha == null ? "" : mergeSha;
  }

  public void setRows(List<PipelineInfo> newRows) {
    rows.clear();
    if (newRows != null) {
      rows.addAll(newRows);
    }
    fireTableDataChanged();
  }

  public PipelineInfo row(int index) {
    return index >= 0 && index < rows.size() ? rows.get(index) : null;
  }

  /** 关注轮循拿到新状态时替换对应行（保持选中位置不跳）。 */
  public void replace(PipelineInfo updated) {
    if (updated == null) {
      return;
    }
    for (int i = 0; i < rows.size(); i++) {
      if (rows.get(i).id().equals(updated.id())) {
        rows.set(i, updated);
        fireTableRowsUpdated(i, i);
        return;
      }
    }
  }

  @Override
  public int getRowCount() {
    return rows.size();
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
  public Object getValueAt(int rowIndex, int columnIndex) {
    PipelineInfo info = rows.get(rowIndex);
    return switch (columnIndex) {
      case 0 -> "#" + info.id();
      case 1 -> info.displayStatus();
      case 2 -> PipelineRole.triggerLabel(info, sourceBranch, targetBranch, mergeSha);
      case 3 -> PipelineRole.shortLabel(info, sourceBranch, targetBranch, mergeSha);
      case 4 -> info.shortSha();
      case 5 -> info.shortCreatedAt();
      default -> "";
    };
  }
}
