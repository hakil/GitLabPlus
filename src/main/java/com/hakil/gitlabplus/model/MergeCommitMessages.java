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
   * 对话框初始提交信息：<b>默认留空</b>。
   *
   * <p>不能预填：GraphQL {@code mergeRequestAccept} 的 {@code commitMessage} 会被 GitLab
   * <b>追加</b>到它自动生成的标题之后，而默认标题与 {@code defaultMergeCommitMessage} 内容相同，
   * 预填就会得到「Merge branch 'x' into 'y'」重复两遍。</p>
   */
  public static String initialMessage() {
    return "";
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
