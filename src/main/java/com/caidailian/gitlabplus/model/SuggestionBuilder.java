package com.caidailian.gitlabplus.model;

import java.util.ArrayList;
import java.util.List;

/**
 * GitLab suggestion（建议修改）的生成与应用。
 *
 * <p>GitLab 没有独立的 suggestion API：它只是 note body 里的代码块语法
 * <code>```suggestion:-N+M</code>，其中 <code>-N</code> 向上覆盖 N 行、
 * <code>+M</code> 向下覆盖 M 行（相对锚定行）。</p>
 */
public final class SuggestionBuilder {

  private SuggestionBuilder() {
  }

  /** 生成 suggestion 代码块（replacement 为建议后的内容）。 */
  public static String build(String replacement, int minusContext, int plusContext) {
    int minus = Math.max(0, minusContext);
    int plus = Math.max(0, plusContext);
    String body = replacement == null ? "" : replacement;
    return "```suggestion:-" + minus + "+" + plus + "\n" + body + "\n```";
  }

  /** 以锚定行为中心，统计可覆盖的上下行数（默认覆盖整个 hunk 的连续新增区域）。 */
  public static int[] defaultContext(List<DiffParser.DiffLine> lines, int anchorIndex) {
    if (lines == null || anchorIndex < 0 || anchorIndex >= lines.size()) {
      return new int[]{0, 0};
    }
    int minus = 0;
    for (int i = anchorIndex - 1; i >= 0; i--) {
      if (lines.get(i).kind() == DiffParser.DiffLine.Kind.ADDED) {
        minus++;
      }
      else {
        break;
      }
    }
    int plus = 0;
    for (int i = anchorIndex + 1; i < lines.size(); i++) {
      if (lines.get(i).kind() == DiffParser.DiffLine.Kind.ADDED) {
        plus++;
      }
      else {
        break;
      }
    }
    return new int[]{minus, plus};
  }

  /**
   * 把建议应用到文件内容（1-based 闭区间行号）。
   *
   * <p>越界或参数不合法时原样返回，绝不破坏文件。</p>
   */
  public static String apply(String fileContent, int startLine, int endLine, String replacement) {
    if (fileContent == null || replacement == null) {
      return fileContent;
    }
    if (startLine < 1 || endLine < startLine) {
      return fileContent;
    }
    String[] lines = fileContent.split("\n", -1);
    if (endLine > lines.length) {
      return fileContent;
    }
    List<String> result = new ArrayList<>();
    for (int i = 0; i < startLine - 1; i++) {
      result.add(lines[i]);
    }
    result.addAll(List.of(replacement.split("\n", -1)));
    for (int i = endLine; i < lines.length; i++) {
      result.add(lines[i]);
    }
    return String.join("\n", result);
  }

  /** 锚定行对应的新文件行号区间（用于 apply）。 */
  public static int[] lineRange(int anchorNewLine, int minusContext, int plusContext) {
    int start = Math.max(1, anchorNewLine - Math.max(0, minusContext));
    int end = Math.max(start, anchorNewLine + Math.max(0, plusContext));
    return new int[]{start, end};
  }
}
