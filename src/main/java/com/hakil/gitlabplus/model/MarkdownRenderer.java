package com.hakil.gitlabplus.model;

import java.util.regex.Pattern;

/**
 * 轻量 Markdown → HTML 渲染器（供 Swing {@code JTextPane} 显示 MR 描述）。
 *
 * <p>不自带完整的 CommonMark 实现，只覆盖 MR 描述里实际会出现的语法：
 * 标题、粗体/斜体、行内代码、代码块、有序/无序列表、引用、链接、换行。</p>
 *
 * <p>安全前提：<b>先做 HTML 转义，再做行内标记替换</b>，所以描述里写的
 * <code>&lt;script&gt;</code> 只会被当作文本显示，不会被当标签执行。</p>
 */
public final class MarkdownRenderer {

  private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
  private static final Pattern ITALIC = Pattern.compile("(?<!\\*)\\*([^*]+)\\*(?!\\*)");
  private static final Pattern CODE = Pattern.compile("`([^`]+)`");
  private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\(([^)\\s]+)\\)");
  private static final Pattern ORDERED = Pattern.compile("^\\d+[.)]\\s+.*");

  private MarkdownRenderer() {
  }

  /** 渲染为完整 HTML 文档（JTextPane 可直接 setText）。 */
  public static String toHtml(String markdown) {
    StringBuilder html = new StringBuilder(
        "<html><body style='font-family:sans-serif;font-size:12px;margin:4px'>");
    if (markdown == null || markdown.isBlank()) {
      return html.append("</body></html>").toString();
    }

    String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
    boolean inCode = false;
    boolean inUl = false;
    boolean inOl = false;

    for (String raw : lines) {
      String trimmed = raw.trim();

      // 代码块围栏
      if (trimmed.startsWith("```")) {
        if (inUl || inOl) {
          html.append(inUl ? "</ul>" : "</ol>");
          inUl = false;
          inOl = false;
        }
        html.append(inCode ? "</pre>" : "<pre style='background:#f4f4f4;padding:4px'>");
        inCode = !inCode;
        continue;
      }
      if (inCode) {
        html.append(escape(raw)).append('\n');
        continue;
      }

      if (trimmed.isEmpty()) {
        if (inUl || inOl) {
          html.append(inUl ? "</ul>" : "</ol>");
          inUl = false;
          inOl = false;
        }
        html.append("<div style='height:6px'></div>");
        continue;
      }

      int level = headingLevel(trimmed);
      if (level > 0) {
        if (inUl || inOl) {
          html.append(inUl ? "</ul>" : "</ol>");
          inUl = false;
          inOl = false;
        }
        String text = inline(escape(trimmed.substring(level).trim()));
        html.append("<h").append(level).append('>').append(text)
            .append("</h").append(level).append('>');
        continue;
      }

      if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
        if (inOl) {
          html.append("</ol>");
          inOl = false;
        }
        if (!inUl) {
          html.append("<ul>");
          inUl = true;
        }
        html.append("<li>").append(inline(escape(trimmed.substring(2).trim()))).append("</li>");
        continue;
      }

      if (ORDERED.matcher(trimmed).matches()) {
        if (inUl) {
          html.append("</ul>");
          inUl = false;
        }
        if (!inOl) {
          html.append("<ol>");
          inOl = true;
        }
        String text = trimmed.replaceFirst("^\\d+[.)]\\s+", "");
        html.append("<li>").append(inline(escape(text))).append("</li>");
        continue;
      }

      if (inUl || inOl) {
        html.append(inUl ? "</ul>" : "</ol>");
        inUl = false;
        inOl = false;
      }

      if (trimmed.startsWith(">")) {
        html.append("<blockquote>").append(inline(escape(trimmed.substring(1).trim())))
            .append("</blockquote>");
        continue;
      }

      // 分隔线
      if (trimmed.matches("^-{3,}$") || trimmed.matches("^\\*{3,}$") || trimmed.matches("^_{3,}$")) {
        html.append("<hr>");
        continue;
      }

      html.append("<div>").append(inline(escape(trimmed))).append("</div>");
    }

    if (inCode) {
      html.append("</pre>");
    }
    if (inUl || inOl) {
      html.append(inUl ? "</ul>" : "</ol>");
    }
    return html.append("</body></html>").toString();
  }

  /** 标题级别（# ~ ######），非标题返回 0。 */
  private static int headingLevel(String trimmed) {
    int count = 0;
    while (count < trimmed.length() && trimmed.charAt(count) == '#') {
      count++;
    }
    if (count >= 1 && count <= 6 && count < trimmed.length() && trimmed.charAt(count) == ' ') {
      return count;
    }
    return 0;
  }

  /** 行内标记（输入已转义）。 */
  static String inline(String escaped) {
    String text = BOLD.matcher(escaped).replaceAll("<b>$1</b>");
    text = ITALIC.matcher(text).replaceAll("<i>$1</i>");
    text = CODE.matcher(text).replaceAll("<code>$1</code>");
    text = LINK.matcher(text).replaceAll("<a href=\"$2\">$1</a>");
    return text;
  }

  /** HTML 转义（含引号，避免属性注入）。 */
  static String escape(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
