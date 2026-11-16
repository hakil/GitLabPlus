package com.caidailian.gitlabplus.model;

import com.caidailian.gitlabplus.api.JsonSupport;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * CI job（REST {@code /pipelines/:id/jobs} 元素）。
 */
public record PipelineJob(String id, String name, String stage, String status, double duration, String webUrl) {

  public static PipelineJob from(JsonObject node) {
    double duration = 0;
    if (node.has("duration") && node.get("duration").isJsonPrimitive()) {
      try {
        duration = node.get("duration").getAsDouble();
      }
      catch (RuntimeException ignored) {
        duration = 0;
      }
    }
    return new PipelineJob(
        JsonSupport.string(node, "id", ""),
        JsonSupport.string(node, "name", ""),
        JsonSupport.string(node, "stage", ""),
        JsonSupport.string(node, "status", ""),
        duration,
        JsonSupport.string(node, "web_url", ""));
  }

  public String durationLabel() {
    if (duration <= 0) {
      return "—";
    }
    long seconds = Math.round(duration);
    return seconds < 60 ? seconds + "s" : (seconds / 60) + "m" + (seconds % 60) + "s";
  }

  public boolean failed() {
    return "failed".equalsIgnoreCase(status);
  }

  /** manual job：需要人工点「运行」才会执行（pipeline 会因此停在手动状态）。 */
  public boolean isManual() {
    return "manual".equalsIgnoreCase(status);
  }

  public boolean isRunning() {
    return "running".equalsIgnoreCase(status) || "pending".equalsIgnoreCase(status);
  }

  /** 可以“运行”（仅 manual）。 */
  public boolean canPlay() {
    return isManual();
  }

  /**
   * 可以重跑：除了「正在跑」和「等待手动」以外的已结束状态都可重跑。
   *
   * <p>这就是「手动触发的 job 跑失败后无法再次运行」的修正点：
   * 失败后状态是 {@code failed}（不再是 manual），应该走 retry 而不是 play。</p>
   */
  public boolean canRetry() {
    String s = status == null ? "" : status.trim().toLowerCase();
    return !isRunning() && !isManual() && !s.isEmpty();
  }

  /** 可以取消：正在跑或排队。 */
  public boolean canCancel() {
    return isRunning();
  }

  /** 解析 jobs 数组元素（可能为 null）。 */
  public static PipelineJob fromNullable(JsonElement element) {
    return element != null && element.isJsonObject() ? from(element.getAsJsonObject()) : null;
  }
}
