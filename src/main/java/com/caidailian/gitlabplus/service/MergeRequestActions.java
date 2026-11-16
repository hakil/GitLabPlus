package com.caidailian.gitlabplus.service;

import com.caidailian.gitlabplus.api.GitLabClient;
import com.caidailian.gitlabplus.api.GraphQLQueries;
import com.caidailian.gitlabplus.api.JsonSupport;
import com.caidailian.gitlabplus.api.RestPaths;
import com.caidailian.gitlabplus.i18n.GitLabPlusBundle;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Merge request 的写操作：合并、审批、状态、草稿、审阅人、rebase。
 *
 * <p>与 {@link MergeRequestApi}（读与创建）分开，纯粹为了让每个类只承担一类变更。</p>
 */
public final class MergeRequestActions {

  /** GraphQL 合并是异步落地的，需要轮询确认。 */
  private static final int MERGE_STATUS_ATTEMPTS = 10;
  private static final long MERGE_STATUS_INTERVAL_MS = 800L;

  private final GitLabClient client;

  public MergeRequestActions(GitLabClient client) {
    this.client = client;
  }

  /**
   * 执行合并。squash=false 时不传 squashCommitMessage（避免服务端校验其形状）。
   */
  public void accept(String projectPath,
                     String iid,
                     String sha,
                     String commitMessage,
                     String squashCommitMessage,
                     boolean squash,
                     boolean removeSourceBranch) {
    if (sha == null || sha.isBlank()) {
      throw new GitLabClient.GitLabApiException(GitLabPlusBundle.message("error.headShaMissing"));
    }
    JsonObject variables = new JsonObject();
    variables.addProperty("projectId", projectPath);
    variables.addProperty("mergeRequestId", iid);
    variables.addProperty("sha", sha);
    variables.addProperty("withSquash", squash);
    variables.addProperty("shouldRemoveSourceBranch", removeSourceBranch);
    addNullable(variables, "commitMessage", commitMessage);
    addNullable(variables, "squashCommitMessage", squash ? squashCommitMessage : null);

    JsonObject data = client.execute(GraphQLQueries.MERGE_REQUEST_ACCEPT, variables);
    JsonObject payload = JsonSupport.obj(data, "mergeRequestAccept");
    if (payload == null) {
      throw new GitLabClient.GitLabApiException(GitLabPlusBundle.message("error.mergeNoResult"));
    }
    String errorText = JsonSupport.errorMessages(JsonSupport.arr(payload, "errors"));
    if (errorText != null) {
      throw new GitLabClient.GitLabApiException(GitLabPlusBundle.message("error.mergeRejected", errorText));
    }
  }

  /**
   * 轮询等待合并状态落地。
   *
   * @return 最终状态（MERGED/LOCKED/... ），始终以 GitLab 返回为准
   */
  public String awaitMerged(String projectPath, String iid) {
    String last = "UNKNOWN";
    for (int attempt = 0; attempt < MERGE_STATUS_ATTEMPTS; attempt++) {
      last = readState(projectPath, iid);
      if ("MERGED".equalsIgnoreCase(last)) {
        return last;
      }
      try {
        Thread.sleep(MERGE_STATUS_INTERVAL_MS);
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return last;
      }
    }
    return last;
  }

  public String readState(String projectPath, String iid) {
    JsonObject variables = new JsonObject();
    variables.addProperty("projectId", projectPath);
    variables.addProperty("iid", iid);
    JsonObject data = client.execute(GraphQLQueries.GET_MERGE_REQUEST_STATE, variables);
    JsonObject project = JsonSupport.obj(data, "project");
    JsonObject mergeRequest = JsonSupport.obj(project, "mergeRequest");
    if (mergeRequest == null) {
      return "UNKNOWN";
    }
    return JsonSupport.string(mergeRequest, "state", "UNKNOWN");
  }

  /**
   * 关闭 / 重开 MR。
   *
   * <p>改走 REST {@code PUT /merge_requests/:iid}（{@code state_event}）：
   * GraphQL 的 {@code MergeRequestNewState} 枚举在不同服务端版本上取值不一致（实测报
   * “Variable $state ... was provided invalid value”），而 REST 的 {@code state_event=close|reopen}
   * 是长期稳定的契约。</p>
   *
   * @param state CLOSE / REOPEN（大小写不敏感）
   */
  public void updateState(String projectPath, String iid, String state) {
    JsonObject payload = new JsonObject();
    payload.addProperty("state_event", MergeRequestApi.stateEvent(state));
    client.restPut(RestPaths.mergeRequest(projectPath, iid), payload);
  }

  public void setDraft(String projectPath, String iid, boolean draft) {
    JsonObject variables = new JsonObject();
    variables.addProperty("projectId", projectPath);
    variables.addProperty("mergeRequestId", iid);
    variables.addProperty("isDraft", draft);
    requireResult(client.execute(GraphQLQueries.MERGE_REQUEST_SET_DRAFT, variables), "mergeRequestSetDraft");
  }

  public void setReviewers(String projectPath, String iid, List<String> usernames) {
    JsonObject variables = new JsonObject();
    variables.addProperty("projectId", projectPath);
    variables.addProperty("mergeRequestId", iid);
    JsonArray array = new JsonArray();
    for (String username : usernames) {
      array.add(username);
    }
    variables.add("reviewerUsernames", array);
    requireResult(client.execute(GraphQLQueries.MERGE_REQUEST_SET_REVIEWERS, variables),
                  "mergeRequestSetReviewers");
  }

  public void rebase(String projectPath, String iid) {
    JsonObject variables = new JsonObject();
    variables.addProperty("projectId", projectPath);
    variables.addProperty("mergeRequestId", iid);
    requireResult(client.execute(GraphQLQueries.MERGE_REQUEST_REBASE, variables), "mergeRequestRebase");
  }

  public void approve(String projectPath, String iid) {
    client.restPost(mergeRequestRestPath(projectPath, iid) + "/approve");
  }

  public void unapprove(String projectPath, String iid) {
    // 先查真实批准状态：未批准就发 unapprove 只会拿到 404，提前拦截并给出明确原因
    if (!userHasApproved(projectPath, iid)) {
      throw new GitLabClient.GitLabApiException(GitLabPlusBundle.message("error.notApproved"), 404, null);
    }
    client.restPost(mergeRequestRestPath(projectPath, iid) + "/unapprove");
  }

  /** 当前账号是否已批准该 MR（approvals 端点的 user_has_approved）。 */
  public boolean userHasApproved(String projectPath, String iid) {
    JsonObject approvals = client.restGet(mergeRequestRestPath(projectPath, iid) + "/approvals");
    return JsonSupport.bool(approvals, "user_has_approved", false);
  }

  // ------------------------------------------------------------------ 内部工具

  private static void requireResult(JsonObject data, String key) {
    JsonObject payload = JsonSupport.obj(data, key);
    if (payload == null) {
      throw new GitLabClient.GitLabApiException(GitLabPlusBundle.message("error.missingResult", key));
    }
    String errors = JsonSupport.errorMessages(JsonSupport.arr(payload, "errors"));
    if (errors != null) {
      throw new GitLabClient.GitLabApiException(
          GitLabPlusBundle.message("error.rejectedByServer", key, errors));
    }
  }

  /** 值为空时显式传 null（与原来的合并行为一致）。 */
  private static void addNullable(JsonObject target, String key, String value) {
    if (value == null || value.isBlank()) {
      target.add(key, JsonNull.INSTANCE);
    }
    else {
      target.addProperty(key, value);
    }
  }

  private static String mergeRequestRestPath(String projectPath, String iid) {
    String encoded = java.net.URLEncoder.encode(projectPath, java.nio.charset.StandardCharsets.UTF_8);
    return "/api/v4/projects/" + encoded + "/merge_requests/" + iid;
  }
}
