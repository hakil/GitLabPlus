package com.hakil.gitlabplus.model;

import com.hakil.gitlabplus.api.JsonSupport;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/**
 * 列表与详情共用的 merge request 视图对象，字段与 GraphQL 查询一一对应。
 */
public final class MergeRequestInfo {

  public final String iid;
  public final String title;
  public final String sourceBranch;
  public final String targetBranch;
  public final String state;
  public final String webUrl;
  public final boolean draft;
  public final boolean mergeable;
  public final String headSha;

  // 详情字段（列表查询时为默认值）
  public final boolean conflicts;
  public final boolean shouldRemoveSourceBranch;
  public final boolean forceRemoveSourceBranch;
  public final boolean squashOnMerge;
  public final boolean squashReadOnly;
  public final boolean userCanMerge;
  public final String defaultMergeCommitMessage;
  public final String defaultSquashCommitMessage;

  private MergeRequestInfo(JsonObject node, boolean withDetails) {
    this.iid = JsonSupport.string(node, "iid", "");
    this.title = JsonSupport.string(node, "title", "");
    this.sourceBranch = JsonSupport.string(node, "sourceBranch", "");
    this.targetBranch = JsonSupport.string(node, "targetBranch", "");
    this.state = JsonSupport.string(node, "state", "");
    this.webUrl = JsonSupport.string(node, "webUrl", "");
    this.draft = JsonSupport.bool(node, "draft", false);
    this.mergeable = JsonSupport.bool(node, "mergeable", false);
    this.headSha = JsonSupport.string(JsonSupport.obj(node, "diffRefs"), "headSha", "");

    if (withDetails) {
      this.conflicts = JsonSupport.bool(node, "conflicts", false);
      this.shouldRemoveSourceBranch = JsonSupport.bool(node, "shouldRemoveSourceBranch", false);
      this.forceRemoveSourceBranch = JsonSupport.bool(node, "forceRemoveSourceBranch", false);
      this.squashOnMerge = JsonSupport.bool(node, "squashOnMerge", false);
      this.squashReadOnly = JsonSupport.bool(node, "squashReadOnly", false);
      this.defaultMergeCommitMessage = JsonSupport.string(node, "defaultMergeCommitMessage", "");
      this.defaultSquashCommitMessage = JsonSupport.string(node, "defaultSquashCommitMessage", "");
      this.userCanMerge = JsonSupport.bool(JsonSupport.obj(node, "userPermissions"), "merge", false);
    }
    else {
      this.conflicts = false;
      this.shouldRemoveSourceBranch = false;
      this.forceRemoveSourceBranch = false;
      this.squashOnMerge = false;
      this.squashReadOnly = false;
      this.userCanMerge = true;
      this.defaultMergeCommitMessage = "";
      this.defaultSquashCommitMessage = "";
    }
  }

  /** 列表节点（字段较少，NULL 字段一律降级为默认值）。 */
  public static MergeRequestInfo fromList(JsonObject node) {
    return new MergeRequestInfo(node, false);
  }

  /**
   * 详情节点（含 diffRefs/userPermissions 等额字段）。
   *
   * <p>目前主链路的详情走 {@code MergeRequestDetails.from}，本方法保留用于解析
   * 简化详情响应（以及自测里对字段容错的校验）。</p>
   */
  public static MergeRequestInfo fromDetails(JsonObject node) {
    return new MergeRequestInfo(node, true);
  }

  public boolean isMerged() {
    return "MERGED".equalsIgnoreCase(state);
  }

  public boolean isOpen() {
    return "OPENED".equalsIgnoreCase(state) || state.isBlank();
  }

  public String displayBranch() {
    return sourceBranch + " → " + targetBranch;
  }

  private static String string(JsonObject o, String key, String fallback) {
    return JsonSupport.string(o, key, fallback);
  }

  public @Nullable String shortTitle() {
    return title == null || title.isBlank() ? "(no title)" : title;
  }
}
