package com.hakil.gitlabplus.model;

import com.hakil.gitlabplus.api.JsonSupport;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * unified diff 解析（P3 基础能力）。
 *
 * <p>GitLab 的 {@code GET /merge_requests/:iid/changes} 返回每个文件一段 unified diff 文本。
 * 行内评论需要精确的 {@code old_line} / {@code new_line}，因此必须自己还原 hunk 与行号映射，
 * 而不是把 diff 当纯文本显示。</p>
 */
public final class DiffParser {

  private static final Pattern HUNK_HEADER =
      Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@(.*)$");

  private DiffParser() {
  }

  /** diff 中的一行。 */
  public record DiffLine(Kind kind, String text, int oldLine, int newLine) {
    public enum Kind {
      CONTEXT(" "),
      ADDED("+"),
      REMOVED("-"),
      NO_NEWLINE("\\");

      private final String prefix;

      Kind(String prefix) {
        this.prefix = prefix;
      }

      public String prefix() {
        return prefix;
      }
    }
  }

  /** 一个 hunk（含起止行号与逐行映射）。 */
  public record Hunk(int oldStart, int oldCount, int newStart, int newCount,
                     String header, List<DiffLine> lines) {
    public boolean containsNewLine(int line) {
      return line >= newStart && line < newStart + Math.max(newCount, 1);
    }
  }

  /** 单个文件的变更。 */
  public record FileDiff(String oldPath, String newPath, boolean newFile, boolean deletedFile,
                         boolean renamedFile, String diff, List<Hunk> hunks) {
    public String displayPath() {
      if (newFile) {
        return newPath;
      }
      if (deletedFile) {
        return oldPath;
      }
      return renamedFile && !oldPath.equals(newPath) ? oldPath + " → " + newPath : newPath;
    }

    public int addedLines() {
      int count = 0;
      for (Hunk hunk : hunks) {
        for (DiffLine line : hunk.lines) {
          if (line.kind == DiffLine.Kind.ADDED) {
            count++;
          }
        }
      }
      return count;
    }

    public int removedLines() {
      int count = 0;
      for (Hunk hunk : hunks) {
        for (DiffLine line : hunk.lines) {
          if (line.kind == DiffLine.Kind.REMOVED) {
            count++;
          }
        }
      }
      return count;
    }
  }

  /** 解析 {@code changes} 数组（REST changes 响应）。 */
  public static List<FileDiff> parseChanges(JsonArray changes) {
    List<FileDiff> result = new ArrayList<>();
    if (changes == null) {
      return result;
    }
    for (JsonElement element : changes) {
      if (element == null || !element.isJsonObject()) {
        continue;
      }
      result.add(fromChange(element.getAsJsonObject()));
    }
    return result;
  }

  public static FileDiff fromChange(JsonObject node) {
    String oldPath = JsonSupport.string(node, "old_path", "");
    String newPath = JsonSupport.string(node, "new_path", oldPath);
    String diff = JsonSupport.string(node, "diff", "");
    return new FileDiff(oldPath,
                        newPath,
                        JsonSupport.bool(node, "new_file", false),
                        JsonSupport.bool(node, "deleted_file", false),
                        JsonSupport.bool(node, "renamed_file", false),
                        diff,
                        parseUnifiedDiff(diff));
  }

  /**
   * 解析 unified diff 文本为 hunk 列表。
   *
   * <p>纯函数：不含 IDE 依赖，异常输入（二进制、空 diff、残缺 hunk）不抛异常，
   * 只跳过无法识别的部分。</p>
   */
  public static List<Hunk> parseUnifiedDiff(String diff) {
    List<Hunk> hunks = new ArrayList<>();
    if (diff == null || diff.isEmpty()) {
      return hunks;
    }
    String[] rawLines = diff.split("\n", -1);
    int index = 0;
    while (index < rawLines.length) {
      String line = stripCr(rawLines[index]);
      Matcher matcher = HUNK_HEADER.matcher(line);
      if (!matcher.matches()) {
        index++;
        continue;
      }
      int oldStart = parseInt(matcher.group(1), 0);
      int oldCount = matcher.group(2) == null ? 1 : parseInt(matcher.group(2), 1);
      int newStart = parseInt(matcher.group(3), 0);
      int newCount = matcher.group(4) == null ? 1 : parseInt(matcher.group(4), 1);
      String header = matcher.group(5) == null ? "" : matcher.group(5).trim();

      List<DiffLine> lines = new ArrayList<>();
      int oldLine = oldStart;
      int newLine = newStart;
      int oldRemaining = oldCount;
      int newRemaining = newCount;
      index++;

      while (index < rawLines.length) {
        String body = stripCr(rawLines[index]);
        if (body.isEmpty()) {
          // 空行在 diff 中代表一个上下文空行
          body = " ";
        }
        char marker = body.charAt(0);
        String text = body.length() > 1 ? body.substring(1) : "";
        if (marker == '\\') {
          // "\ No newline at end of file"：紧跟在一行之后，不消耗 hunk 计数
          lines.add(new DiffLine(DiffLine.Kind.NO_NEWLINE, text, -1, -1));
          index++;
          continue;
        }
        if (oldRemaining <= 0 && newRemaining <= 0) {
          break;
        }
        if (marker == ' ') {
          lines.add(new DiffLine(DiffLine.Kind.CONTEXT, text, oldLine, newLine));
          oldLine++;
          newLine++;
          oldRemaining--;
          newRemaining--;
        }
        else if (marker == '+') {
          lines.add(new DiffLine(DiffLine.Kind.ADDED, text, -1, newLine));
          newLine++;
          newRemaining--;
        }
        else if (marker == '-') {
          lines.add(new DiffLine(DiffLine.Kind.REMOVED, text, oldLine, -1));
          oldLine++;
          oldRemaining--;
        }
        else {
          break;
        }
        index++;
      }
      hunks.add(new Hunk(oldStart, oldCount, newStart, newCount, header, List.copyOf(lines)));
    }
    return hunks;
  }

  /**
   * 为新文件中的某一行找到可用的评论锚点。
   *
   * @return 命中时返回该行的 {@link DiffLine}（其 {@code oldLine} 可能为 -1，表示新增行）
   */
  public static DiffLine findNewLine(List<Hunk> hunks, int newLineNumber) {
    for (Hunk hunk : hunks) {
      for (DiffLine line : hunk.lines) {
        if (line.newLine == newLineNumber) {
          return line;
        }
      }
    }
    return null;
  }

  private static int parseInt(String value, int fallback) {
    try {
      return Integer.parseInt(value);
    }
    catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static String stripCr(String line) {
    return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
  }
}
