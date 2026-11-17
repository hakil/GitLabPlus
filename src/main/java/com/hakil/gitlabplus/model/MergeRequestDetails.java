package com.hakil.gitlabplus.model;

import com.hakil.gitlabplus.api.JsonSupport;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * MR 全量详情（对齐官方插件 review 详情页展示的信息面）。
 */
public final class MergeRequestDetails {

  public final String iid;
  public final String title;
  public final String description;
  public final String state;
  public final String webUrl;
  public final boolean draft;
  public final boolean mergeable;
  public final boolean conflicts;
  public final String createdAt;
  public final String updatedAt;

  public final String sourceBranch;
  public final String targetBranch;
  public final String headSha;

  public final boolean shouldRemoveSourceBranch;
  public final boolean forceRemoveSourceBranch;
  public final boolean squashOnMerge;
  public final boolean squashReadOnly;
  public final String defaultMergeCommitMessage;
  public final String defaultSquashCommitMessage;

  public final String author;
  public final List<String> assignees;
  public final List<String> reviewers;
  public final List<String> labels;
  public final List<String> approvedBy;

  public final boolean canApprove;
  public final boolean canMerge;
  public final boolean canCreateNote;
  public final boolean canUpdate;

  public final String pipelineStatus;

  private MergeRequestDetails(JsonObject node) {
    this.iid = JsonSupport.string(node, "iid", "");
    this.title = JsonSupport.string(node, "title", "");
    this.description = JsonSupport.string(node, "description", "");
    this.state = JsonSupport.string(node, "state", "");
    this.webUrl = JsonSupport.string(node, "webUrl", "");
    this.draft = JsonSupport.bool(node, "draft", false);
    this.mergeable = JsonSupport.bool(node, "mergeable", false);
    this.conflicts = JsonSupport.bool(node, "conflicts", false);
    this.createdAt = JsonSupport.string(node, "createdAt", "");
    this.updatedAt = JsonSupport.string(node, "updatedAt", "");
    this.sourceBranch = JsonSupport.string(node, "sourceBranch", "");
    this.targetBranch = JsonSupport.string(node, "targetBranch", "");
    this.headSha = JsonSupport.string(JsonSupport.obj(node, "diffRefs"), "headSha", "");
    this.shouldRemoveSourceBranch = JsonSupport.bool(node, "shouldRemoveSourceBranch", false);
    this.forceRemoveSourceBranch = JsonSupport.bool(node, "forceRemoveSourceBranch", false);
    this.squashOnMerge = JsonSupport.bool(node, "squashOnMerge", false);
    this.squashReadOnly = JsonSupport.bool(node, "squashReadOnly", false);
    this.defaultMergeCommitMessage = JsonSupport.string(node, "defaultMergeCommitMessage", "");
    this.defaultSquashCommitMessage = JsonSupport.string(node, "defaultSquashCommitMessage", "");

    this.author = JsonSupport.string(JsonSupport.obj(node, "author"), "username", "");
    this.assignees = names(node, "assignees");
    this.reviewers = names(node, "reviewers");
    this.labels = labelTitles(node);
    this.approvedBy = names(node, "approvedBy");

    JsonObject permissions = JsonSupport.obj(node, "userPermissions");
    this.canApprove = JsonSupport.bool(permissions, "canApprove", false);
    this.canMerge = JsonSupport.bool(permissions, "canMerge", false);
    this.canCreateNote = JsonSupport.bool(permissions, "createNote", false);
    this.canUpdate = JsonSupport.bool(permissions, "updateMergeRequest", false);

    this.pipelineStatus = JsonSupport.string(JsonSupport.obj(node, "headPipeline"), "status", "");
  }

  public static MergeRequestDetails from(JsonObject node) {
    return new MergeRequestDetails(node);
  }

  public boolean isOpen() {
    return "OPENED".equalsIgnoreCase(state) || state.isBlank();
  }

  public boolean isMerged() {
    return "MERGED".equalsIgnoreCase(state);
  }

  public String displayBranch() {
    return sourceBranch + " → " + targetBranch;
  }

  /** 一句话状态摘要，供列表与标题栏使用。 */
  public String statusSummary() {
    StringBuilder sb = new StringBuilder(state);
    if (draft) {
      sb.append(" · Draft");
    }
    if (conflicts) {
      sb.append(" · 冲突");
    }
    if (!mergeable) {
      sb.append(" · 不可合并");
    }
    if (approvedBy != null && !approvedBy.isEmpty()) {
      sb.append(" · 已批准 ").append(approvedBy.size());
    }
    if (pipelineStatus != null && !pipelineStatus.isBlank()) {
      sb.append(" · CI ").append(pipelineStatus);
    }
    return sb.toString();
  }

  private static List<String> names(JsonObject node, String field) {
    List<String> result = new ArrayList<>();
    JsonObject connection = JsonSupport.obj(node, field);
    JsonArray nodes = JsonSupport.arr(connection, "nodes");
    if (nodes == null) {
      return result;
    }
    for (JsonElement element : nodes) {
      if (element != null && element.isJsonObject()) {
        String username = JsonSupport.string(element.getAsJsonObject(), "username", "");
        if (!username.isBlank()) {
          result.add(username);
        }
      }
    }
    return result;
  }

  private static List<String> labelTitles(JsonObject node) {
    List<String> result = new ArrayList<>();
    JsonObject connection = JsonSupport.obj(node, "labels");
    JsonArray nodes = JsonSupport.arr(connection, "nodes");
    if (nodes == null) {
      return result;
    }
    for (JsonElement element : nodes) {
      if (element != null && element.isJsonObject()) {
        String title = JsonSupport.string(element.getAsJsonObject(), "title", "");
        if (!title.isBlank()) {
          result.add(title);
        }
      }
    }
    return result;
  }
}
