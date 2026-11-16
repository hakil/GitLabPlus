package com.caidailian.gitlabplus.model;

/**
 * 流水线在「一个 MR 的生命周期」里扮演的角色。
 *
 * <p>GitLab 原始字段 {@code source}（push / merge_request_event / schedule）本身不足以判断
 * 意义：同样是 {@code push}，发生在源分支是「提交触发」，发生在目标分支且 sha 等于
 * merge commit 才是「合并结果」。所以这里把 {@code ref} + {@code sha} + MR 上下文
 * 三者合起来给出语义标签。</p>
 */
public final class PipelineRole {

  /** 角色类型（含中文描述）。 */
  public enum Kind {
    MERGE_RESULT("合并结果", "合并"),
    TARGET_BRANCH("目标分支", "目标分支"),
    SOURCE_MR("MR 校验", "MR 校验"),
    SOURCE_PUSH("源分支", "推送"),
    SCHEDULE("定时", "定时"),
    OTHER("其他", "其他");

    private final String description;
    private final String shortLabel;

    Kind(String description, String shortLabel) {
      this.description = description;
      this.shortLabel = shortLabel;
    }

    public String getDescription() {
      return description;
    }

    /** 「来源」列用的短标签。 */
    public String getShortLabel() {
      return shortLabel;
    }
  }

  private PipelineRole() {
  }

  /**
   * 判定角色。
   *
   * @param info         流水线
   * @param sourceBranch MR 源分支（可空）
   * @param targetBranch MR 目标分支（可空）
   * @param mergeSha     MR 的 merge commit sha（未合并时为空）
   */
  public static Kind classify(PipelineInfo info, String sourceBranch, String targetBranch, String mergeSha) {
    if (info == null) {
      return Kind.OTHER;
    }
    if (notBlank(mergeSha) && mergeSha.equalsIgnoreCase(nullToEmpty(info.sha()))) {
      return Kind.MERGE_RESULT;
    }
    if (notBlank(targetBranch) && targetBranch.equals(nullToEmpty(info.ref()))) {
      return Kind.TARGET_BRANCH;
    }
    if (notBlank(sourceBranch) && sourceBranch.equals(nullToEmpty(info.ref()))) {
      return "merge_request_event".equalsIgnoreCase(nullToEmpty(info.source()))
          ? Kind.SOURCE_MR
          : Kind.SOURCE_PUSH;
    }
    if ("schedule".equalsIgnoreCase(nullToEmpty(info.source()))) {
      return Kind.SCHEDULE;
    }
    return Kind.OTHER;
  }

  /**
   * 「触发」列：箭头表达式，**方向统一向右**（代码流向）。
   *
   * <ul>
   *   <li>合并结果：{@code feature/login → dev}（来自源分支，合并到目标分支）</li>
   *   <li>MR 校验：{@code feature/login → dev}（将在合并前校验）</li>
   *   <li>推送 / 目标分支 / 定时：只显示分支名（无方向）</li>
   * </ul>
   *
   * <p>两者都跑在不同分支上（合并结果跑在目标分支，MR 校验跑在源分支），
   * 「跑在哪个分支」由「来源」列区分。</p>
   */
  public static String triggerLabel(PipelineInfo info, String sourceBranch, String targetBranch, String mergeSha) {
    if (info == null) {
      return "";
    }
    String ref = nullToEmpty(info.ref());
    Kind kind = classify(info, sourceBranch, targetBranch, mergeSha);
    return switch (kind) {
      case MERGE_RESULT -> notBlank(sourceBranch)
          ? sourceBranch + " → " + (notBlank(targetBranch) ? targetBranch : ref)
          : ref;
      case SOURCE_MR -> notBlank(targetBranch) ? ref + " → " + targetBranch : ref;
      default -> ref;
    };
  }

  /** 「来源」列：合并 / MR 校验 / 推送 / 目标分支 / 定时（无角色时退回 GitLab 原始来源）。 */
  public static String shortLabel(PipelineInfo info, String sourceBranch, String targetBranch, String mergeSha) {
    if (info == null) {
      return "";
    }
    Kind kind = classify(info, sourceBranch, targetBranch, mergeSha);
    return kind == Kind.OTHER ? info.sourceLabel() : kind.getShortLabel();
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
