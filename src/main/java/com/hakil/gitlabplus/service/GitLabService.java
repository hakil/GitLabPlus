package com.hakil.gitlabplus.service;

import com.hakil.gitlabplus.api.GitLabClient;
import com.hakil.gitlabplus.model.MergeRequestDetails;
import com.hakil.gitlabplus.model.MergeRequestInfo;
import com.hakil.gitlabplus.model.PipelineInfo;
import com.hakil.gitlabplus.model.PipelineJob;
import com.hakil.gitlabplus.model.ProjectMember;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Merge request 能力的统一门面。
 *
 * <p>实现按资源拆到三个类里，这里只做转发，目的是：</p>
 * <ul>
 *   <li>UI 侧只依赖一个入口，不必四处拼装 api/actions/pipeline；</li>
 *   <li>每个实现类只承担一类职责，各自的体量可读。</li>
 * </ul>
 *
 * <p>{@link #listMergeRequests}、{@link #createMergeRequest}、{@link #relatedPipelines}
 * 返回门面自己的 record，与实现类的同名 record 保持一一转换 ——
 * 这样调用点无需感知内部拆分。</p>
 */
public final class GitLabService {

  private final GitLabClient client;
  private final MergeRequestApi requests;
  private final MergeRequestActions actions;
  private final PipelineApi pipelines;

  public GitLabService(String serverUrl, String token) {
    this(new GitLabClient(serverUrl, token));
  }

  /** 复用已有客户端（自检、评审、Diff 共用同一连接配置）。 */
  public GitLabService(GitLabClient client) {
    this.client = client;
    this.requests = new MergeRequestApi(client);
    this.actions = new MergeRequestActions(client);
    this.pipelines = new PipelineApi(client);
  }

  public GitLabClient client() {
    return client;
  }

  public MergeRequestApi requests() {
    return requests;
  }

  public MergeRequestActions actions() {
    return actions;
  }

  public PipelineApi pipelines() {
    return pipelines;
  }

  // ------------------------------------------------------------------ 列表与详情

  /** 一页列表结果（cursor 分页）。 */
  public record Page(List<MergeRequestInfo> items, boolean hasNextPage, String endCursor) {
  }

  /**
   * 列出项目下 merge request。
   *
   * @param state GraphQL MergeRequestState 名（opened / merged / closed / all），null 表示不限
   */
  public Page listMergeRequests(String projectPath, String state, String cursor) {
    MergeRequestApi.Page page = requests.listMergeRequests(projectPath, state, cursor);
    return new Page(page.items(), page.hasNextPage(), page.endCursor());
  }

  /** GraphQL 返回 {@code project: null} 时用 REST 逐层定位原因。 */
  public String diagnose(String projectPath) {
    return requests.diagnose(projectPath);
  }

  public MergeRequestDetails loadFullDetails(String projectPath, String iid) {
    return requests.loadFullDetails(projectPath, iid);
  }

  /** 项目成员（审阅人选项）。 */
  public List<ProjectMember> listProjectMembers(String projectPath) {
    return requests.listProjectMembers(projectPath);
  }

  // ------------------------------------------------------------------ 写操作

  /** 执行合并。squash=false 时不传 squashCommitMessage（避免服务端校验其形状）。 */
  public void accept(String projectPath,
                     String iid,
                     String sha,
                     String commitMessage,
                     String squashCommitMessage,
                     boolean squash,
                     boolean removeSourceBranch) {
    actions.accept(projectPath, iid, sha, commitMessage, squashCommitMessage, squash, removeSourceBranch);
  }

  /** 轮询等待合并状态落地（以 GitLab 返回为准）。 */
  public String awaitMerged(String projectPath, String iid) {
    return actions.awaitMerged(projectPath, iid);
  }

  public String readState(String projectPath, String iid) {
    return actions.readState(projectPath, iid);
  }

  /** 关闭 / 重开 MR（REST {@code state_event}，避免 GraphQL 枚举跨版本不一致）。 */
  public void updateState(String projectPath, String iid, String state) {
    actions.updateState(projectPath, iid, state);
  }

  public void setDraft(String projectPath, String iid, boolean draft) {
    actions.setDraft(projectPath, iid, draft);
  }

  public void setReviewers(String projectPath, String iid, List<String> usernames) {
    actions.setReviewers(projectPath, iid, usernames);
  }

  public void rebase(String projectPath, String iid) {
    actions.rebase(projectPath, iid);
  }

  public void approve(String projectPath, String iid) {
    actions.approve(projectPath, iid);
  }

  public void unapprove(String projectPath, String iid) {
    actions.unapprove(projectPath, iid);
  }

  /** 当前账号是否已批准该 MR（approvals 端点的 user_has_approved）。 */
  public boolean userHasApproved(String projectPath, String iid) {
    return actions.userHasApproved(projectPath, iid);
  }

  // ------------------------------------------------------------------ 创建 MR

  /** 创建结果（iid 用于后续操作，webUrl 用于打开网页）。 */
  public record Created(String iid, String webUrl) {
  }

  /** 创建 merge request（REST v4，字段名下划线风格）。 */
  public Created createMergeRequest(String projectPath,
                                    String sourceBranch,
                                    String targetBranch,
                                    String title,
                                    String description,
                                    boolean removeSourceBranch,
                                    boolean squash) {
    MergeRequestApi.Created created = requests.createMergeRequest(
        projectPath, sourceBranch, targetBranch, title, description, removeSourceBranch, squash);
    return new Created(created.iid(), created.webUrl());
  }

  // ------------------------------------------------------------------ CI

  /** MR 关联流水线的聚合结果（带分支/合并提交上下文）。 */
  public record RelatedPipelines(String sourceBranch, String targetBranch, String mergeSha,
                                 List<PipelineInfo> pipelines) {
  }

  /** MR 关联的全部 pipeline（新到旧，仅源分支触发的那部分）。 */
  public List<PipelineInfo> mergeRequestPipelines(String projectPath, String iid) {
    return pipelines.mergeRequestPipelines(projectPath, iid);
  }

  /** 收集与 MR 相关的全部 pipeline（多来源聚合 + 去重）。 */
  public RelatedPipelines relatedPipelines(String projectPath, String iid) {
    PipelineApi.RelatedPipelines related = pipelines.relatedPipelines(projectPath, iid);
    return new RelatedPipelines(related.sourceBranch(), related.targetBranch(), related.mergeSha(),
                                related.pipelines());
  }

  /** REST 版 MR 详情（取 merge_commit_sha / 分支名）。 */
  public JsonObject mergeRequestRestDetails(String projectPath, String iid) {
    return pipelines.mergeRequestRestDetails(projectPath, iid);
  }

  /** 按 pipeline id 取状态（关注轮循用，避免多个 pipeline 时取错）。 */
  public PipelineInfo pipelineById(String projectPath, String pipelineId) {
    return pipelines.pipelineById(projectPath, pipelineId);
  }

  /** job 执行日志（trace 纯文本）。 */
  public String jobTrace(String projectPath, String jobId) {
    return pipelines.jobTrace(projectPath, jobId);
  }

  public List<PipelineJob> pipelineJobs(String projectPath, String pipelineId) {
    return pipelines.pipelineJobs(projectPath, pipelineId);
  }

  public void playJob(String projectPath, String jobId) {
    pipelines.playJob(projectPath, jobId);
  }

  public void retryJob(String projectPath, String jobId) {
    pipelines.retryJob(projectPath, jobId);
  }

  public void cancelJob(String projectPath, String jobId) {
    pipelines.cancelJob(projectPath, jobId);
  }

  public void retryPipeline(String projectPath, String pipelineId) {
    pipelines.retryPipeline(projectPath, pipelineId);
  }

  public void cancelPipeline(String projectPath, String pipelineId) {
    pipelines.cancelPipeline(projectPath, pipelineId);
  }

  // ------------------------------------------------------------------ 静态工具（转发）

  /** 判断异常消息是否为「已有 open MR」冲突。 */
  public static boolean looksLikeConflict(String message) {
    return MergeRequestApi.looksLikeConflict(message);
  }

  /** 从 409 的响应消息里提取已存在的 MR 号（如 "...: !217"）。 */
  public static String existingMergeRequestRef(String message) {
    return MergeRequestApi.existingMergeRequestRef(message);
  }

  /** 把 CLOSE/REOPEN（或 closed/reopened）归一成 REST 的 state_event 值。 */
  public static String stateEvent(String state) {
    return MergeRequestApi.stateEvent(state);
  }
}
