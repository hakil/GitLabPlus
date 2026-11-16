package com.caidailian.gitlabplus.ui;

import com.caidailian.gitlabplus.model.PipelineJob;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/** CI 页签下表（job 列表）的模型。 */
public final class JobTableModel extends AbstractTableModel {

  private final String[] columns = {"Job", "Stage", "状态", "耗时"};
  private final List<PipelineJob> rows = new ArrayList<>();

  public void setRows(List<PipelineJob> newRows) {
    rows.clear();
    if (newRows != null) {
      rows.addAll(newRows);
    }
    fireTableDataChanged();
  }

  public PipelineJob row(int index) {
    return index >= 0 && index < rows.size() ? rows.get(index) : null;
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
    PipelineJob job = rows.get(rowIndex);
    return switch (columnIndex) {
      case 0 -> job.name();
      case 1 -> job.stage();
      case 2 -> job.status();
      case 3 -> job.durationLabel();
      default -> "";
    };
  }
}
