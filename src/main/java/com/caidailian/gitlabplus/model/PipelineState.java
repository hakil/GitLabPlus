package com.caidailian.gitlabplus.model;

/**
 * 流水线状态分类。
 *
 * <p>关键点：GitLab 的 pipeline status 不只有「执行中 / 结束」两类 ——
 * {@code manual}（等待手动触发）、{@code blocked}（等待人工放行）
 * 属于<b>非执行但也不会自动结束</b>的状态；把这类当成执行中会导致
 * 关注一直等到超时，所以必须单独归类并立即停止关注。</p>
 */
public final class PipelineState {

  /** 状态归类。 */
  public enum Bucket {
    /** 仍在推进（created/waiting_for_resource/preparing/pending/running）。 */
    RUNNING,
    /** 正常结束。 */
    SUCCESS,
    /** 失败结束（failed/canceled）。 */
    FAILURE,
    /** 需要人工介入，不会自己结束（manual/blocked）。 */
    NEEDS_MANUAL,
    /** 未知状态（新版本引入的状态等）。 */
    UNKNOWN
  }

  private PipelineState() {
  }

  public static Bucket classify(String status) {
    if (status == null || status.isBlank()) {
      return Bucket.UNKNOWN;
    }
    return switch (status.trim().toLowerCase()) {
      case "success" -> Bucket.SUCCESS;
      case "failed", "canceled", "cancelled" -> Bucket.FAILURE;
      case "skipped" -> Bucket.SUCCESS;
      case "manual", "blocked" -> Bucket.NEEDS_MANUAL;
      case "created", "waiting_for_resource", "preparing", "pending", "running", "scheduled" -> Bucket.RUNNING;
      default -> Bucket.UNKNOWN;
    };
  }

  /** 是否仍在推进（值得继续轮询）。 */
  public static boolean isRunning(String status) {
    return classify(status) == Bucket.RUNNING;
  }

  /**
   * 是否应结束关注。
   *
   * <p>只有 {@link Bucket#RUNNING} 需要继续轮询；终态、需人工、以及<b>未知状态</b>
   * 都立即结束 —— 未知状态无法预判是否会自行结束，继续等只会耗尽超时窗口。
   * 未知状态会以 WARNING 通知告知用户，用户可手动刷新或重新关注。</p>
   */
  public static boolean shouldStopWatching(String status) {
    return classify(status) != Bucket.RUNNING;
  }

  /** 是否成功（含 skipped）。 */
  public static boolean isSuccess(String status) {
    return classify(status) == Bucket.SUCCESS;
  }

  /** 中文展示名。 */
  public static String displayName(String status) {
    if (status == null || status.isBlank()) {
      return "未知";
    }
    return switch (status.trim().toLowerCase()) {
      case "created" -> "已创建";
      case "waiting_for_resource" -> "等待资源";
      case "preparing" -> "准备中";
      case "pending" -> "排队中";
      case "running" -> "执行中";
      case "success" -> "成功";
      case "failed" -> "失败";
      case "canceled", "cancelled" -> "已取消";
      case "skipped" -> "已跳过";
      case "manual" -> "等待手动触发";
      case "blocked" -> "等待人工放行";
      case "scheduled" -> "已排期";
      default -> status;
    };
  }

  /** 关注结束时的通知提示语。 */
  public static String watchConclusion(String status) {
    return switch (classify(status)) {
      case SUCCESS -> "流水线已成功结束（" + displayName(status) + "）";
      case FAILURE -> "流水线已失败结束（" + displayName(status) + "）";
      case NEEDS_MANUAL -> "流水线在等待人工操作（" + displayName(status) + "），已停止关注";
      case RUNNING -> "流水线仍在执行（" + displayName(status) + "）";
      case UNKNOWN -> "流水线状态未知（" + status + "），已停止关注";
    };
  }
}
