package com.caidailian.gitlabplus.ui;

import com.intellij.util.ui.UIUtil;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;

/**
 * 状态列着色：失败红 / 成功绿 / 执行中蓝 / 需人工橙。
 *
 * <p>判定用「中文展示名或原始状态名都识别」，因为同一列里两种值都会出现
 * （pipeline 行显示 {@code displayStatus()}，job 行显示原始 {@code status}）。</p>
 */
public final class StatusRenderer extends DefaultTableCellRenderer {

  private static final Color COLOR_FAILURE = Color.RED.darker();
  private static final Color COLOR_SUCCESS = new Color(0, 128, 0);
  private static final Color COLOR_RUNNING = new Color(0, 100, 180);
  private static final Color COLOR_MANUAL = new Color(160, 110, 0);

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                 boolean focused, int row, int column) {
    Component component = super.getTableCellRendererComponent(table, value, selected, focused, row, column);
    if (!selected && value != null) {
      component.setForeground(colorOf(value.toString()));
    }
    return component;
  }

  /** 单个状态的展示颜色（纯函数，便于单测）。 */
  public static Color colorOf(String text) {
    if (text == null) {
      return UIUtil.getLabelForeground();
    }
    if (text.contains("失败") || "failed".equalsIgnoreCase(text) || "canceled".equalsIgnoreCase(text)) {
      return COLOR_FAILURE;
    }
    if (text.contains("成功") || "success".equalsIgnoreCase(text)) {
      return COLOR_SUCCESS;
    }
    if (text.contains("执行中") || text.contains("排队") || "running".equalsIgnoreCase(text)
        || "pending".equalsIgnoreCase(text)) {
      return COLOR_RUNNING;
    }
    if (text.contains("手动") || text.contains("人工") || "manual".equalsIgnoreCase(text)) {
      return COLOR_MANUAL;
    }
    return UIUtil.getLabelForeground();
  }
}
