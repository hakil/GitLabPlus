package test;

import org.junit.jupiter.api.Test;
import com.hakil.gitlabplus.model.AnsiStripper;

/**
 * ANSI 清理自测（job 日志出现方块字符的根因）。
 */
class AnsiStripperTest extends SuiteTestSupport {

    @Test
  void suite() {
    String green = "\u001B[32mOK\u001B[0m";
    TestAssert.eq("CSI 颜色序列被移除", "OK", AnsiStripper.strip(green));

    String bold = "\u001B[1;33mWARN\u001B[0m something";
    TestAssert.eq("多参数 CSI", "WARN something", AnsiStripper.strip(bold));

    String section = "section_start:1699:build\r\u001B[0K";
    TestAssert.eq("GitLab section 行清理", "section_start:1699:build",
                      AnsiStripper.strip(section));

    // OSC 是终端控制序列（改窗口标题用），其内容不应出现在日志里
    TestAssert.eq("OSC 标题序列整体丢弃", "", AnsiStripper.strip("\u001B]0;title\u0007"));

    // ESC + 任意字符是两字节控制序列，整对丢弃
    TestAssert.eq("两字节控制序列整体丢弃", "", AnsiStripper.strip("\u001Bx"));
    TestAssert.eq("控制序列后续文本保留", "abc", AnsiStripper.strip("\u001Bxabc"));

    TestAssert.eq("CR 被移除", "a\nb", AnsiStripper.strip("a\r\nb"));

    TestAssert.eq("无 ANSI 原样", "plain text", AnsiStripper.strip("plain text"));

    TestAssert.eq("中文不变", "构建成功", AnsiStripper.strip("构建成功"));

    TestAssert.eq("null → 空", "", AnsiStripper.strip(null));
    TestAssert.eq("空串 → 空", "", AnsiStripper.strip(""));

    TestAssert.eq("hasAnsi 检测 ESC", true, AnsiStripper.hasAnsi("\u001B[0m"));
    TestAssert.eq("hasAnsi 检测 CR", true, AnsiStripper.hasAnsi("a\rb"));
    TestAssert.eq("hasAnsi 普通文本", false, AnsiStripper.hasAnsi("plain"));
    TestAssert.eq("hasAnsi null", false, AnsiStripper.hasAnsi(null));
  }
}
