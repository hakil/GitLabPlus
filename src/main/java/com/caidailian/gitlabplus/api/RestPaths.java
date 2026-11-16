package com.caidailian.gitlabplus.api;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

/**
 * GitLab REST v4 路径构造（评审相关）。
 *
 * <p>端点形状来源：反编译官方插件
 * {@code GitLabMergeRequestCommentsApiKt} / {@code GitLabMergeRequestDraftNotesApiKt}
 * / {@code GitLabMergeRequestReactionsApiKt}。官方在评审能力上走 REST 而非 GraphQL。</p>
 */
public final class RestPaths {

  private RestPaths() {
  }

  /** 项目全路径需编码为单段（{@code a/b} → {@code a%2Fb}）。 */
  public static String encode(String projectPath) {
    return URLEncoder.encode(projectPath, StandardCharsets.UTF_8).replace("+", "%20");
  }

  public static String mergeRequests(String projectPath) {
    return "/api/v4/projects/" + encode(projectPath) + "/merge_requests";
  }

  public static String mergeRequest(String projectPath, String iid) {
    return mergeRequests(projectPath) + "/" + iid;
  }

  public static String changes(String projectPath, String iid) {
    return mergeRequest(projectPath, iid) + "/changes";
  }

  /** MR 版本列表：diff_refs 缺失时用它取三元素 sha（新到旧）。 */
  public static String mergeRequestVersions(String projectPath, String iid) {
    return mergeRequest(projectPath, iid) + "/versions";
  }

  /** 原始文件内容（原生 diff 对比两侧用）。 */
  public static String repositoryRawFile(String projectPath, String filePath, String ref) {
    StringBuilder url = new StringBuilder("/api/v4/projects/")
        .append(encode(projectPath))
        .append("/repository/files/")
        .append(encode(filePath))
        .append("/raw");
    if (ref != null && !ref.isBlank()) {
      url.append("?ref=").append(encode(ref));
    }
    return url.toString();
  }

  /** 项目成员（含继承成员），用于审阅人选择。 */
  public static String projectMembers(String projectPath, int perPage) {
    return "/api/v4/projects/" + encode(projectPath)
        + "/members/all?per_page=" + perPage;
  }

  public static String discussions(String projectPath, String iid) {
    return mergeRequest(projectPath, iid) + "/discussions";
  }

  public static String discussion(String projectPath, String iid, String discussionId) {
    return discussions(projectPath, iid) + "/" + discussionId;
  }

  public static String discussionNotes(String projectPath, String iid, String discussionId) {
    return discussion(projectPath, iid, discussionId) + "/notes";
  }

  public static String notes(String projectPath, String iid) {
    return mergeRequest(projectPath, iid) + "/notes";
  }

  public static String note(String projectPath, String iid, String noteId) {
    return notes(projectPath, iid) + "/" + noteId;
  }

  public static String awardEmoji(String projectPath, String iid, String noteId) {
    return note(projectPath, iid, noteId) + "/award_emoji";
  }

  public static String awardEmoji(String projectPath, String iid, String noteId, String awardId) {
    return awardEmoji(projectPath, iid, noteId) + "/" + awardId;
  }

  public static String mergeRequestPipelines(String projectPath, String iid) {
    return mergeRequest(projectPath, iid) + "/pipelines";
  }

  public static String pipelineJobs(String projectPath, String pipelineId) {
    return "/api/v4/projects/" + encode(projectPath) + "/pipelines/" + pipelineId + "/jobs?per_page=100";
  }

  public static String pipeline(String projectPath, String pipelineId) {
    return "/api/v4/projects/" + encode(projectPath) + "/pipelines/" + pipelineId;
  }

  /** 项目级 pipeline 列表（可按 ref / sha 过滤）。 */
  public static String pipelines(String projectPath) {
    return "/api/v4/projects/" + encode(projectPath) + "/pipelines";
  }

  /** 按分支过滤的 pipeline 列表。 */
  public static String pipelinesByRef(String projectPath, String ref, int perPage) {
    return pipelines(projectPath) + "?ref=" + encode(ref) + "&per_page=" + perPage + "&order_by=id&sort=desc";
  }

  /** 按提交 sha 过滤的 pipeline 列表（合并后的流水线就属于 merge commit）。 */
  public static String pipelinesBySha(String projectPath, String sha, int perPage) {
    return pipelines(projectPath) + "?sha=" + encode(sha) + "&per_page=" + perPage + "&order_by=id&sort=desc";
  }

  /** 手动触发一个 manual job。 */
  public static String jobPlay(String projectPath, String jobId) {
    return "/api/v4/projects/" + encode(projectPath) + "/jobs/" + jobId + "/play";
  }

  /** job 执行日志（trace 纯文本）。 */
  public static String jobTrace(String projectPath, String jobId) {
    return "/api/v4/projects/" + encode(projectPath) + "/jobs/" + jobId + "/trace";
  }

  /** 重跑 job（failed / canceled / success 均可）。 */
  public static String jobRetry(String projectPath, String jobId) {
    return "/api/v4/projects/" + encode(projectPath) + "/jobs/" + jobId + "/retry";
  }

  /** 取消 job（running / pending）。 */
  public static String jobCancel(String projectPath, String jobId) {
    return "/api/v4/projects/" + encode(projectPath) + "/jobs/" + jobId + "/cancel";
  }

  /** 重跑整个 pipeline。 */
  public static String pipelineRetry(String projectPath, String pipelineId) {
    return "/api/v4/projects/" + encode(projectPath) + "/pipelines/" + pipelineId + "/retry";
  }

  /** 取消整个 pipeline。 */
  public static String pipelineCancel(String projectPath, String pipelineId) {
    return "/api/v4/projects/" + encode(projectPath) + "/pipelines/" + pipelineId + "/cancel";
  }

  /** 讨论笔记列表（drafts 等能力已移除，不再请求 draft_notes）。 */

  public static String approve(String projectPath, String iid) {
    return mergeRequest(projectPath, iid) + "/approve";
  }

  public static String unapprove(String projectPath, String iid) {
    return mergeRequest(projectPath, iid) + "/unapprove";
  }
}
