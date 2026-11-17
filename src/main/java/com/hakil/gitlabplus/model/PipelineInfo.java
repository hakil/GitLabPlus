package com.hakil.gitlabplus.model;

import com.hakil.gitlabplus.api.JsonSupport;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * MR 的 head pipeline 摘要（REST {@code /merge_requests/:iid/pipelines} 或 {@code /pipelines} 元素）。
 */
public record PipelineInfo(String id, String status, String ref, String sha, String webUrl,
                           String source, String createdAt) {

  public static PipelineInfo from(JsonObject node) {
    return new PipelineInfo(
        JsonSupport.string(node, "id", ""),
        JsonSupport.string(node, "status", ""),
        JsonSupport.string(node, "ref", ""),
        JsonSupport.string(node, "sha", ""),
        JsonSupport.string(node, "web_url", ""),
        JsonSupport.string(node, "source", ""),
        JsonSupport.string(node, "created_at", ""));
  }

  public static PipelineInfo fromNullable(JsonElement element) {
    return element != null && element.isJsonObject() ? from(element.getAsJsonObject()) : null;
  }

  public boolean isRunning() {
    return PipelineState.isRunning(status);
  }

  public boolean terminalOrManual() {
    return PipelineState.shouldStopWatching(status);
  }

  public String displayStatus() {
    return PipelineState.displayName(status);
  }

  public String shortSha() {
    return sha == null || sha.length() <= 8 ? (sha == null ? "" : sha) : sha.substring(0, 8);
  }

  /** 触发来源的中文名（区分「合并后目标分支的 push」与「MR 事件」）。 */
  public String sourceLabel() {
    if (source == null || source.isBlank()) {
      return "";
    }
    return switch (source.trim().toLowerCase()) {
      case "push" -> "推送";
      case "merge_request_event" -> "MR 事件";
      case "external_pull_request_event" -> "外部 PR";
      case "schedule" -> "定时";
      case "web" -> "网页";
      case "api" -> "API";
      case "pipeline" -> "Pipeline";
      case "trigger" -> "触发器";
      case "parent_pipeline" -> "父流水线";
      case "ondemand_dast_scan" -> "DAST";
      case "chat" -> "聊天";
      case "webide" -> "WebIDE";
      default -> source;
    };
  }

  /** 创建时间的简短展示（yyyy-MM-dd HH:mm，取原始字符串前 16 位）。 */
  public String shortCreatedAt() {
    if (createdAt == null || createdAt.isBlank()) {
      return "";
    }
    return createdAt.length() >= 16 ? createdAt.substring(0, 16).replace('T', ' ') : createdAt;
  }
}
