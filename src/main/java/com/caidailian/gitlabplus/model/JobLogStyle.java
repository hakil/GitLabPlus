package com.caidailian.gitlabplus.model;

import java.util.Locale;

/**
 * Job 日志行的着色分类（供 UI 用不同颜色渲染）。
 *
 * <p>优先级：错误 → 警告 → 成功 → 分组 → 命令 → 普通。
 * 判定全部基于关键字与行首特征，纯函数，便于单测。</p>
 */
public enum JobLogStyle {

  ERROR("错误"),
  WARN("警告"),
  SUCCESS("成功"),
  SECTION("分组"),
  COMMAND("命令"),
  NORMAL("普通");

  private final String description;

  JobLogStyle(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  /** 判定一行日志的类别（null / 空行视为普通）。 */
  public static JobLogStyle classify(String line) {
    if (line == null || line.isBlank()) {
      return NORMAL;
    }
    String upper = line.toUpperCase(Locale.ROOT);

    if (containsAny(upper, "ERROR", "FATAL", "FAILURE", "FAILED", "EXCEPTION", "CAUSED BY")) {
      return ERROR;
    }
    if (containsAny(upper, "WARN", "WARNING", "DEPRECATED")) {
      return WARN;
    }
    if (containsAny(upper, "BUILD SUCCESS", "SUCCESS", "SUCCEEDED", "PASSED")) {
      return SUCCESS;
    }
    if (upper.contains("SECTION_START") || upper.contains("SECTION_END")) {
      return SECTION;
    }
    if (line.startsWith("$")
        || upper.startsWith("RUNNING WITH GITLAB-RUNNER")
        || upper.startsWith("EXECUTING ")
        || upper.startsWith("PREPARING THE ")) {
      return COMMAND;
    }
    return NORMAL;
  }

  private static boolean containsAny(String text, String... keywords) {
    for (String keyword : keywords) {
      if (text.contains(keyword)) {
        return true;
      }
    }
    return false;
  }
}
