package com.caidailian.gitlabplus.model;

import java.util.regex.Pattern;

/**
 * 去除 GitLab job 日志里的 ANSI 控制序列与回车符。
 *
 * <p>job trace 里混有大量终端控制序列（{@code ESC[32m} 之类），
 * JTextPane 不认识它们，会渲染成方块/乱码；{@code \r} 还会让同一行被覆盖显示。
 * 展示前统一清理，保留纯文本内容。</p>
 */
public final class AnsiStripper {

  /** CSI 序列、OSC 序列、以及孤立的 ESC。 */
  private static final Pattern ANSI = Pattern.compile(
      "\u001B\\[[0-9;?]*[\\x20-\\x2F]*[\\x40-\\x7E]"   // CSI ... 终止字符
          + "|\u001B\\][^\u0007]*\u0007"                // OSC ... BEL
          + "|\u001B\\][^\u001B]*\u001B\\\\"            // OSC ... ST
          + "|\u001B.");                                // 其它两字节序列

  private AnsiStripper() {
  }

  /** 去掉 ANSI 序列与 CR；null/空返回空串。 */
  public static String strip(String text) {
    if (text == null || text.isEmpty()) {
      return text == null ? "" : text;
    }
    String result = ANSI.matcher(text).replaceAll("");
    if (result.indexOf('\r') >= 0) {
      result = result.replace("\r", "");
    }
    return result;
  }

  /** 是否含有需要清理的控制字符（便于测试与诊断）。 */
  public static boolean hasAnsi(String text) {
    return text != null && (text.indexOf('\u001B') >= 0 || text.indexOf('\r') >= 0);
  }
}
