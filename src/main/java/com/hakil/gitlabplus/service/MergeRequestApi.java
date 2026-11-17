package com.hakil.gitlabplus.service;

import com.hakil.gitlabplus.api.GitLabClient;
import com.hakil.gitlabplus.api.JsonSupport;
import com.hakil.gitlabplus.api.MergeRequestCapabilities;
import com.hakil.gitlabplus.api.RestPaths;
import com.hakil.gitlabplus.i18n.GitLabPlusBundle;
import com.hakil.gitlabplus.model.MergeRequestDetails;
import com.hakil.gitlabplus.model.MergeRequestInfo;
import com.hakil.gitlabplus.model.ProjectMember;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Merge request 的读与创建：列表、详情、诊断、创建。
 *
 * <p>所有方法同步阻塞，调用方须自行放到后台线程执行。</p>
 */
public final class MergeRequestApi {

  private final GitLabClient client;

  public MergeRequestApi(GitLabClient client) {
    this.client = client;
  }

  /**
   * 列出项目下 merge request。
   *
   * @param state GraphQL MergeRequestState 名（opened / merged / closed / all），null 表示不限
   */
  public Page listMergeRequests(String projectPath, String state, String cursor) {
    JsonObject variables = new JsonObject();
    variables.addProperty("projectId", projectPath);
    variables.addProperty("pageSize", 50);
    if (cursor != null && !cursor.isBlank()) {
      variables.addProperty("cursor", cursor);
    }
    if (state != null && !state.isBlank()) {
      variables.addProperty("state", state);
    }

    JsonObject data = client.execute(
        MergeRequestCapabilities.listQuery(MergeRequestCapabilities.forServer(client)), variables);
    JsonObject project = JsonSupport.obj(data, "project");
    if (project == null) {
      throw new GitLabClient.GitLabApiException(
          GitLabPlusBundle.message("error.projectNull", projectPath, diagnose(projectPath)));
    }
    JsonObject connection = JsonSupport.obj(project, "mergeRequests");
    List<MergeRequestInfo> items = new ArrayList<>();
    if (connection != null) {
      JsonArray nodes = JsonSupport.arr(connection, "nodes");
      if (nodes != null) {
        for (JsonElement node : nodes) {
          if (node != null && node.isJsonObject()) {
            items.add(MergeRequestInfo.fromList(node.getAsJsonObject()));
          }
        }
      }
    }
    JsonObject pageInfo = JsonSupport.obj(connection, "pageInfo");
    boolean hasNext = JsonSupport.bool(pageInfo, "hasNextPage", false);
    String endCursor = JsonSupport.string(pageInfo, "endCursor", null);
    return new Page(items, hasNext, endCursor);
  }

  /** 一页列表结果（cursor 分页）。 */
  public record Page(List<MergeRequestInfo> items, boolean hasNextPage, String endCursor) {
  }

  /**
   * GraphQL 把「项目不可见」统一返回为 {@code project: null}，无法区分原因。
   * 这里用 REST 逐层定位：token 是否有效 → 项目是否对该 token 可见。
   */
  public String diagnose(String projectPath) {
    StringBuilder sb = new StringBuilder("诊断：");
    String user;
    try {
      JsonObject currentUser = client.restGet("/api/v4/user");
      user = JsonSupport.string(currentUser, "username", "unknown");
      sb.append("token 有效（当前用户 ").append(user).append("）");
    }
    catch (GitLabClient.GitLabApiException e) {
      sb.append("token 校验失败（").append(e.getMessage())
          .append("）—— 请重新创建 Access Token，scope 需勾选 api");
      return sb.toString();
    }

    String encoded = java.net.URLEncoder.encode(projectPath, java.nio.charset.StandardCharsets.UTF_8);
    try {
      JsonObject project = client.restGet("/api/v4/projects/" + encoded);
      String fullPath = JsonSupport.string(project, "path_with_namespace", projectPath);
      sb.append("；REST 可见项目 ").append(fullPath).append(" → 说明 GraphQL 查询与该服务端版本不兼容");
    }
    catch (GitLabClient.GitLabApiException e) {
      sb.append("；REST 访问该项目失败（").append(e.getMessage())
          .append("）→ 项目路径不对，或当前账号不是该项目的成员");
    }
    return sb.toString();
  }

  public MergeRequestDetails loadFullDetails(String projectPath, String iid) {
    JsonObject variables = new JsonObject();
    variables.addProperty("projectId", projectPath);
    variables.addProperty("iid", iid);
    JsonObject data = client.execute(
        MergeRequestCapabilities.detailsQuery(MergeRequestCapabilities.forServer(client)), variables);
    JsonObject project = JsonSupport.obj(data, "project");
    if (project == null) {
      throw new GitLabClient.GitLabApiException(
          GitLabPlusBundle.message("error.projectNull", projectPath, diagnose(projectPath)));
    }
    JsonObject mergeRequest = JsonSupport.obj(project, "mergeRequest");
    if (mergeRequest == null) {
      throw new GitLabClient.GitLabApiException(GitLabPlusBundle.message("error.mrNotFound", iid));
    }
    return MergeRequestDetails.from(mergeRequest);
  }

  /** 创建结果（iid 用于后续操作，webUrl 用于打开网页）。 */
  public record Created(String iid, String webUrl) {
  }

  /**
   * 创建 merge request（REST v4）。
   *
   * <p>端点 {@code POST /projects/:id/merge_requests}，字段名使用下划线风格，
   * 与 GraphQL 的 createMergeRequest 等价但不受版本门槛影响。</p>
   */
  public Created createMergeRequest(String projectPath,
                                    String sourceBranch,
                                    String targetBranch,
                                    String title,
                                    String description,
                                    boolean removeSourceBranch,
                                    boolean squash) {
    if (sourceBranch == null || sourceBranch.isBlank()) {
      throw new GitLabClient.GitLabApiException(GitLabPlusBundle.message("error.branchRequired"));
    }
    if (targetBranch == null || targetBranch.isBlank()) {
      throw new GitLabClient.GitLabApiException(GitLabPlusBundle.message("error.targetRequired"));
    }
    if (title == null || title.isBlank()) {
      throw new GitLabClient.GitLabApiException(GitLabPlusBundle.message("error.titleRequired"));
    }
    JsonObject payload = new JsonObject();
    payload.addProperty("source_branch", sourceBranch);
    payload.addProperty("target_branch", targetBranch);
    payload.addProperty("title", title);
    if (description != null && !description.isBlank()) {
      payload.addProperty("description", description);
    }
    payload.addProperty("remove_source_branch", removeSourceBranch);
    payload.addProperty("squash", squash);

    JsonObject response;
    try {
      response = client.restPost(RestPaths.mergeRequests(projectPath), payload);
    }
    catch (GitLabClient.GitLabApiException e) {
      // 409 = 该源分支已有打开的 MR（重复创建），给出可操作的提示而不是裸 HTTP 错误
      if (e.statusCode() == 409 || looksLikeConflict(e.getMessage())) {
        throw conflictException(sourceBranch, e.getMessage(), e);
      }
      throw e;
    }
    catch (RuntimeException e) {
      // 某些平台路径会把 409 包成 IOException 抛出，消息里只带状态码
      if (looksLikeConflict(e.getMessage())) {
        throw conflictException(sourceBranch, e.getMessage(), e);
      }
      throw e;
    }
    String iid = JsonSupport.string(response, "iid", "");
    String webUrl = JsonSupport.string(response, "web_url", "");
    if (iid.isBlank()) {
      throw new GitLabClient.GitLabApiException(GitLabPlusBundle.message("error.createNoIid"));
    }
    return new Created(iid, webUrl);
  }

  /**
   * 项目成员（含继承成员）——审阅人选项。
   *
   * <p>{@code /projects/:id/members/all} 比 {@code /members} 多出上级群组继承的成员，
   * 否则大型群组下会出现「负责人不在列表里」。</p>
   */
  public List<ProjectMember> listProjectMembers(String projectPath) {
    JsonElement element = client.restJsonElement("GET", RestPaths.projectMembers(projectPath, 100), null);
    List<ProjectMember> result = new ArrayList<>();
    if (element != null && element.isJsonArray()) {
      for (JsonElement item : element.getAsJsonArray()) {
        if (item != null && item.isJsonObject()) {
          ProjectMember member = ProjectMember.from(item.getAsJsonObject());
          if (!member.username().isBlank()) {
            result.add(member);
          }
        }
      }
    }
    return result;
  }

  // ------------------------------------------------------------------ 共享静态工具

  /** 判断异常消息是否为「已有 open MR」冲突。 */
  public static boolean looksLikeConflict(String message) {
    if (message == null) {
      return false;
    }
    return message.contains("409")
        || message.contains("Another open merge request already exists")
        || message.contains("already exists");
  }

  /** 从 409 的响应消息里提取已存在的 MR 号（如 "...: !217"）。 */
  public static String existingMergeRequestRef(String message) {
    if (message == null) {
      return "";
    }
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("!(\\d+)").matcher(message);
    return matcher.find() ? "!" + matcher.group(1) : "";
  }

  /** 把 CLOSE/REOPEN（或 closed/reopened）归一成 REST 的 state_event 值。 */
  public static String stateEvent(String state) {
    if (state == null) {
      return "close";
    }
    String normalized = state.trim().toLowerCase();
    return switch (normalized) {
      case "close", "closed" -> "close";
      case "reopen", "reopened" -> "reopen";
      default -> normalized;
    };
  }

  private static GitLabClient.GitLabApiException conflictException(String sourceBranch, String message,
                                                                   Throwable cause) {
    String existing = existingMergeRequestRef(message);
    return new GitLabClient.GitLabApiException(
        GitLabPlusBundle.message("error.duplicateMr", sourceBranch,
                                 existing.isBlank() ? "" : "（" + existing + "）"), 409, cause);
  }
}
