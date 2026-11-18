package test;

import com.hakil.gitlabplus.ui.CreateMergeRequestForm;
import org.junit.jupiter.api.Test;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Container;
import java.util.List;

/**
 * 新建 MR 表单布局：同一列的所有控件必须等宽。
 *
 * <p>回归现场问题：标题输入框比分支下拉窄一大截。根因有两条——
 * {@code FormBuilder} 的控件列 {@code weightx=0} 不拉伸；
 * 且 {@code JTextField#setPreferredSize} 不生效（宽度由列数决定）。</p>
 */
class CreateMergeRequestFormTest extends SuiteTestSupport {

  @Test
  void fieldsShareSameColumnWidth() {
    JComboBox<String> combo = new JComboBox<>(new String[]{"feature/login"});
    JTextField title = new JTextField(32);
    JTextArea description = new JTextArea(4, 20);
    JPanel panel = CreateMergeRequestForm.build(List.of(
        new CreateMergeRequestForm.Row("源分支：", combo, false),
        new CreateMergeRequestForm.Row("标题：", title, false),
        new CreateMergeRequestForm.Row("描述：", new JScrollPane(description), false)));

    panel.setSize(760, 460);
    layoutDeep(panel);
    int wideCombo = combo.getWidth();
    TestAssert.eq("宽尺寸下标题与下拉等宽", wideCombo, title.getWidth());
    TestAssert.isTrue("标题不再被压缩（> 400px）", title.getWidth() > 400);

    panel.setSize(500, 460);
    layoutDeep(panel);
    TestAssert.eq("窄尺寸下标题与下拉等宽", combo.getWidth(), title.getWidth());
    TestAssert.isTrue("窄尺寸下仍可用（> 250px）", title.getWidth() > 250);
    TestAssert.isTrue("窄尺寸比宽尺寸小", combo.getWidth() < wideCombo);
  }

  @Test
  void minimumSizeIsUsable() {
    TestAssert.isTrue("最小宽度 >= 400", CreateMergeRequestForm.minimumSize().width >= 400);
    TestAssert.isTrue("最小高度 >= 300", CreateMergeRequestForm.minimumSize().height >= 300);
    TestAssert.isTrue("空行列表不抛异常", CreateMergeRequestForm.build(List.of()) != null);
    TestAssert.isTrue("组件为 null 的行被跳过",
                      CreateMergeRequestForm.build(
                          List.of(new CreateMergeRequestForm.Row("标题：", null, false))) != null);
  }

  private static void layoutDeep(Container container) {
    container.doLayout();
    for (Component component : container.getComponents()) {
      if (component instanceof Container child) {
        layoutDeep(child);
      }
    }
  }
}
