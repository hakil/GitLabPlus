package com.caidailian.gitlabplus.service;

import com.caidailian.gitlabplus.api.GitLabClient;
import com.caidailian.gitlabplus.api.JsonSupport;
import com.caidailian.gitlabplus.api.RestPaths;
import com.caidailian.gitlabplus.model.PipelineInfo;
import com.caidailian.gitlabplus.model.PipelineJob;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 流水线（pipeline）与 job 的查询与操作。
 *
 * <p>单独成类的原因：CI 这一块的数据来源与 MR 本身不同（REST 三组端点 + 数值 id 限制），
 * 混在 merge request 服务里会让人误以为可以复用 GraphQL 的全局 id。</p>
 */
public final class PipelineApi {

  private final GitLabClient client;

  public PipelineApi(GitLabClient client) {
    this.client = client;
  }

  /**
   * MR 关联的 pipeline（只含源分支触发的那些）。
   *
   * <p>一个 MR 可能同时关联多个 pipeline：源分支每次提交一个、合并后目标分支上一个。
   * 只取第一个会让人看不到其他 pipeline，所以这里返回全部并交给 UI 选择。</p>
   */
  public List<PipelineInfo> mergeRequestPipelines(String projectPath, String iid) {
    JsonElement element = client.restJsonElement(
        "GET", RestPaths.mergeRequestPipelines(projectPath, iid), null);
    List<PipelineInfo> result = new ArrayList<>();
    if (element != null && element.isJsonArray()) {
      for (JsonElement item : element.getAsJsonArray()) {
        PipelineInfo info = PipelineInfo.fromNullable(item);
        if (info != null) {
          result.add(info);
        }
      }
    }
    return result;
  }

  /** MR 相关流水线的聚合结果：带分支/合并提交上下文，供 UI 判定每条流水线的角色。 */
  public record RelatedPipelines(String sourceBranch, String targetBranch, String mergeSha,
                                 List<PipelineInfo> pipelines) {
  }

  /**
   * 收集与一个 MR 相关的全部 pipeline（多来源聚合 + 去重）。
   *
   * <p>{@code /merge_requests/:iid/pipelines} 只包含<b>源分支</b>触发的 pipeline，
   * 合并后在目标分支跑的那条不在其中。所以额外用：</p>
   * <ul>
   *   <li>{@code ?sha=<merge_commit_sha>}：精确拿到合并提交触发的 pipeline</li>
   *   <li>{@code ?ref=<target_branch>}：目标分支最近的 pipeline（兼容取不到 merge sha 的情况）</li>
   *   <li>{@code ?ref=<source_branch>}：源分支后续的重跑</li>
   * </ul>
   */
  public RelatedPipelines relatedPipelines(String projectPath, String iid) {
    JsonObject mergeRequest = mergeRequestRestDetails(projectPath, iid);
    String mergeSha = JsonSupport.string(mergeRequest, "merge_commit_sha", "");
    if (mergeSha.isBlank()) {
      mergeSha = JsonSupport.string(mergeRequest, "squash_commit_sha", "");
    }
    String sourceBranch = JsonSupport.string(mergeRequest, "source_branch", "");
    String targetBranch = JsonSupport.string(mergeRequest, "target_branch", "");

    List<PipelineInfo> collected = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    appendDistinct(collected, seen, mergeRequestPipelines(projectPath, iid));
    if (!mergeSha.isBlank()) {
      appendDistinct(collected, seen, pipelinesByQuery(RestPaths.pipelinesBySha(projectPath, mergeSha, 20)));
    }
    if (!targetBranch.isBlank()) {
      appendDistinct(collected, seen,
                     pipelinesByQuery(RestPaths.pipelinesByRef(projectPath, targetBranch, 20)));
    }
    if (!sourceBranch.isBlank()) {
      appendDistinct(collected, seen,
                     pipelinesByQuery(RestPaths.pipelinesByRef(projectPath, sourceBranch, 20)));
    }
    collected.sort((left, right) -> compareIdDesc(left.id(), right.id()));
    List<PipelineInfo> limited = collected.size() > 30 ? collected.subList(0, 30) : collected;
    return new RelatedPipelines(sourceBranch, targetBranch, mergeSha, new ArrayList<>(limited));
  }

  /** REST 版 MR 详情（取 merge_commit_sha / 分支名）。 */
  public JsonObject mergeRequestRestDetails(String projectPath, String iid) {
    return client.restGet(RestPaths.mergeRequest(projectPath, iid));
  }

  /** 按 pipeline id 取状态（关注轮循用，避免一个 MR 多个 pipeline 时取错）。 */
  public PipelineInfo pipelineById(String projectPath, String pipelineId) {
    return PipelineInfo.from(client.restGet(RestPaths.pipeline(projectPath, pipelineId)));
  }

  /** job 执行日志（trace 纯文本）。 */
  public String jobTrace(String projectPath, String jobId) {
    return client.restGetText(RestPaths.jobTrace(projectPath, jobId));
  }

  /** 按 pipeline id 取 job 列表。 */
  public List<PipelineJob> pipelineJobs(String projectPath, String pipelineId) {
    JsonElement jobs = client.restJsonElement("GET", RestPaths.pipelineJobs(projectPath, pipelineId), null);
    List<PipelineJob> result = new ArrayList<>();
    if (jobs != null && jobs.isJsonArray()) {
      for (JsonElement element : jobs.getAsJsonArray()) {
        PipelineJob job = PipelineJob.fromNullable(element);
        if (job != null) {
          result.add(job);
        }
      }
    }
    return result;
  }

  /** 手动触发 manual job（等价于 GitLab 网页上的运行按钮）。 */
  public void playJob(String projectPath, String jobId) {
    client.restPost(RestPaths.jobPlay(projectPath, jobId));
  }

  /** 重跑 job（failed / canceled / success 均可）。 */
  public void retryJob(String projectPath, String jobId) {
    client.restPost(RestPaths.jobRetry(projectPath, jobId));
  }

  /** 取消 job（running / pending）。 */
  public void cancelJob(String projectPath, String jobId) {
    client.restPost(RestPaths.jobCancel(projectPath, jobId));
  }

  /** 重跑整个 pipeline。 */
  public void retryPipeline(String projectPath, String pipelineId) {
    client.restPost(RestPaths.pipelineRetry(projectPath, pipelineId));
  }

  /** 取消整个 pipeline。 */
  public void cancelPipeline(String projectPath, String pipelineId) {
    client.restPost(RestPaths.pipelineCancel(projectPath, pipelineId));
  }

  private static void appendDistinct(List<PipelineInfo> target, Set<String> seen,
                                     List<PipelineInfo> source) {
    for (PipelineInfo info : source) {
      if (info != null && seen.add(info.id())) {
        target.add(info);
      }
    }
  }

  private static int compareIdDesc(String left, String right) {
    try {
      return Long.compare(Long.parseLong(right), Long.parseLong(left));
    }
    catch (NumberFormatException e) {
      return right.compareTo(left);
    }
  }

  private List<PipelineInfo> pipelinesByQuery(String path) {
    JsonElement element = client.restJsonElement("GET", path, null);
    List<PipelineInfo> result = new ArrayList<>();
    if (element != null && element.isJsonArray()) {
      for (JsonElement item : element.getAsJsonArray()) {
        PipelineInfo info = PipelineInfo.fromNullable(item);
        if (info != null) {
          result.add(info);
        }
      }
    }
    return result;
  }
}
