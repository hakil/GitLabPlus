package com.caidailian.gitlabplus.model;

/**
 * Job 执行日志的展示处理（tail 截断、行数统计）。
 *
 * <p>GitLab 的 trace 可能有几 MB，UI 直接全量渲染会卡；
 * 这里只保留尾部若干行，并明确告知省略了多少行。</p>
 */
public final class JobLog {

  /** 默认展示的最大行数。 */
  public static final int DEFAULT_MAX_LINES = 4000;

  private JobLog() {
  }

  /**
   * 取日志尾部最多 {@code maxLines} 行。
   *
   * @return 不超过限制时原样返回；超限时前置一行省略提示
   */
  public static String tail(String text, int maxLines) {
    if (text == null || text.isEmpty()) {
      return text == null ? "" : text;
    }
    if (maxLines <= 0) {
      return "";
    }
    String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
    String[] lines = normalized.split("\n", -1);
    if (lines.length <= maxLines) {
      return text;
    }
    int omitted = lines.length - maxLines;
    StringBuilder sb = new StringBuilder();
    sb.append("…（已省略前面 ").append(omitted).append(" 行，只显示最后 ")
        .append(maxLines).append(" 行；日志共 ").append(lines.length).append(" 行）\n");
    for (int i = omitted; i < lines.length; i++) {
      sb.append(lines[i]);
      if (i < lines.length - 1) {
        sb.append('\n');
      }
    }
    return sb.toString();
  }

  /** 行数（空文本计 0）。 */
  public static int lineCount(String text) {
    if (text == null || text.isEmpty()) {
      return 0;
    }
    return text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1).length;
  }
}
