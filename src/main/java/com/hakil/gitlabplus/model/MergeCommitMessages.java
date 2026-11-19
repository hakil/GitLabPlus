package com.hakil.gitlabplus.model;

/**
 * 合并提交信息的取值规则。
 *
 * <p>合并（merge）与压缩合并（squash）的提交信息<b>互斥</b>：同时提交两者会让同一标题
 * 在合并产生的提交里出现两次。因此这里集中决定「发哪个、发什么、初始显示什么」，
 * 供对话框与服务层共用，避免两侧各写一套判断。</p>
 */
public final class MergeCommitMessages {

  private MergeCommitMessages() {
  }

  /** 是否需要发送合并提交信息：非 squash 时发送，squash 时为 {@code null}（不发送）。 */
  public static String merge(boolean squash, String text) {
    return squash ? null : blankToNull(text);
  }

  /** 是否需要发送压缩提交信息：squash 时发送，非 squash 时为 {@code null}（不发送）。 */
  public static String squash(boolean squash, String text) {
    return squash ? blankToNull(text) : null;
  }

  /**
   * 对话框初始提交信息：按 GitLab 默认值预填，用户可见可改。
   *
   * <p>squash 取压缩默认值，否则取合并默认值；都为空时退到 MR 标题。</p>
   */
  public static String initialMessage(boolean squash,
                                      String defaultSquash,
                                      String defaultMerge,
                                      String title) {
    String primary = squash ? defaultSquash : defaultMerge;
    if (primary != null && !primary.isBlank()) {
      return primary;
    }
    return title == null ? "" : title;
  }

  /**
   * 用户实际修改过的提交信息。
   *
   * <p>与初始值相同（忽略首尾空白）时返回空串，表示<b>不发送</b>该字段：GraphQL
   * {@code mergeRequestAccept} 的 {@code commitMessage} 会被 GitLab 追加到它自动生成的标题之后，
   * 发送与默认标题相同的文本会让「Merge branch 'x' into 'y'」出现两遍。</p>
   */
  public static String customized(String current, String initial) {
    String text = current == null ? "" : current.trim();
    String baseline = initial == null ? "" : initial.trim();
    return text.equals(baseline) ? "" : text;
  }

  /** 展示用：GitLab 在该场景会使用的默认提交标题（供对话框提示）。 */
  public static String defaultMessage(boolean squash,
                                      String defaultSquash,
                                      String defaultMerge,
                                      String title) {
    String primary = squash ? defaultSquash : defaultMerge;
    if (primary != null && !primary.isBlank()) {
      return primary;
    }
    return title == null ? "" : title;
  }

  /** squash 勾选框的初始状态：从「Squash and Merge」进入，或该 MR 本身已设置 squash。 */
  public static boolean defaultSquash(boolean forceSquash, boolean squashOnMerge) {
    return forceSquash || squashOnMerge;
  }

  /** squash 勾选框是否可修改：强制 squash 时不可取消；服务端标记 squashReadOnly 时不可改。 */
  public static boolean squashEditable(boolean forceSquash, boolean squashReadOnly) {
    return !forceSquash && !squashReadOnly;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
