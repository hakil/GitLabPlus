package com.hakil.gitlabplus.ui;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

/**
 * 「新建 MR」对话框的表单布局。
 *
 * <p>不用 {@code FormBuilder}：它对控件列使用 {@code weightx=0}，
 * 而 {@code JTextField#setPreferredSize} 在文本组件上不生效（宽度仍由列数决定），
 * 两者叠加会让标题框停在列数宽度、明显窄于分支下拉。</p>
 *
 * <p>这里 label 列固定、控件列 {@code weightx=1 + fill=HORIZONTAL}，
 * 同一列的所有控件宽度恒等且随对话框缩放。</p>
 */
public final class CreateMergeRequestForm {

  /** 一行控件；{@code fullWidth} 表示该行控件横跨 label 列与控件列（提示文字、勾选框）。 */
  public record Row(String label, JComponent component, boolean fullWidth) {
  }

  private CreateMergeRequestForm() {
  }

  public static JPanel build(List<Row> rows) {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints labelCell = new GridBagConstraints();
    labelCell.gridx = 0;
    labelCell.gridy = 0;
    labelCell.anchor = GridBagConstraints.NORTHWEST;
    labelCell.insets = new Insets(4, 0, 4, 8);

    GridBagConstraints fieldCell = new GridBagConstraints();
    fieldCell.gridx = 1;
    fieldCell.gridy = 0;
    fieldCell.weightx = 1;
    fieldCell.fill = GridBagConstraints.HORIZONTAL;
    fieldCell.anchor = GridBagConstraints.WEST;
    fieldCell.insets = new Insets(4, 0, 4, 0);

    for (Row row : rows) {
      if (row.component() == null) {
        continue;
      }
      if (row.fullWidth()) {
        GridBagConstraints cell = new GridBagConstraints();
        cell.gridx = 0;
        cell.gridy = labelCell.gridy;
        cell.gridwidth = 2;
        cell.weightx = 1;
        cell.fill = GridBagConstraints.HORIZONTAL;
        cell.anchor = GridBagConstraints.WEST;
        cell.insets = new Insets(2, 0, 2, 0);
        panel.add(row.component(), cell);
      }
      else {
        panel.add(new JLabel(row.label()), labelCell);
        panel.add(row.component(), fieldCell);
      }
      labelCell.gridy++;
      fieldCell.gridy++;
    }

    // 把多余高度留在底部，避免控件被纵向拉伸
    GridBagConstraints filler = new GridBagConstraints();
    filler.gridx = 0;
    filler.gridy = labelCell.gridy;
    filler.gridwidth = 2;
    filler.weighty = 1;
    filler.fill = GridBagConstraints.BOTH;
    panel.add(new JPanel(), filler);
    return panel;
  }

  /** 表单的最小可用宽度（对话框缩到更窄时控件会挤在一起）。 */
  public static Dimension minimumSize() {
    return new Dimension(420, 320);
  }
}
