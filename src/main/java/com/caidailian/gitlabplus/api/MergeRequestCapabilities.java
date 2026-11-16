package com.caidailian.gitlabplus.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端 GraphQL 能力探测与查询构造。
 *
 * <p>背景（真实故障）：不同 GitLab 版本 {@code MergeRequest} 类型上的字段不同 ——
 * 例如老版本没有 {@code squashReadOnly}，而无 diff 的 MR 上 {@code DiffRefs.headSha}
 * 又会返回 null 触发非空约束错误。硬编码字段集必然随版本漂移而整体失败，
 * 因此这里在运行时做 introspection，按实际支持的字段拼装查询。</p>
 *
 * <p>introspection 不可用（被禁用/网络失败）时退回稳定字段集，保证基本可用。</p>
 */
public final class MergeRequestCapabilities {

  private static final Map<String, Caps> CACHE = new ConcurrentHashMap<>();

  private static final String INTROSPECTION = """
      query {
        mr: __type(name: "MergeRequest") { fields { name } }
        perm: __type(name: "MergeRequestPermissions") { fields { name } }
      }
      """;

  /** 列表所需的最小稳定集（任何版本都存在）。 */
  private static final Set<String> MINIMAL_MR = Set.of(
      "iid", "title", "sourceBranch", "targetBranch", "state", "webUrl", "mergeable");

  /** 详情可选字段的探测清单（按需启用）。 */
  private static final List<String> DETAIL_CANDIDATES = List.of(
      "description", "draft", "conflicts", "createdAt", "updatedAt",
      "shouldRemoveSourceBranch", "forceRemoveSourceBranch", "squashOnMerge", "squashReadOnly",
      "defaultMergeCommitMessage", "defaultSquashCommitMessage",
      "author", "assignees", "reviewers", "labels", "approvedBy", "userPermissions", "headPipeline");

  private static final Set<String> MINIMAL_PERM = Set.of("canMerge");

  private MergeRequestCapabilities() {
  }

  /** 探测结果：MergeRequest 与 MergeRequestPermissions 上可用字段。 */
  public record Caps(Set<String> mergeRequest, Set<String> permissions, boolean introspected) {
    public boolean mr(String field) {
      return mergeRequest.contains(field);
    }

    public boolean perm(String field) {
      return permissions.contains(field);
    }
  }

  /** 探测并缓存（按服务器地址 + 当前用户）。
   *
   * <p>权限相关字段（canApprove / createNote…）是<b>按当前用户</b>返回的，
   * 所以缓存键必须包含用户名：换 token（换账号）后不能沿用上个账号的能力集。</p>
   */
  public static Caps forServer(GitLabClient client) {
    String key = cacheKey(client);
    Caps cached = CACHE.get(key);
    if (cached != null) {
      return cached;
    }
    Caps fetched = introspect(client);
    CACHE.put(key, fetched);
    return fetched;
  }

  private static String cacheKey(GitLabClient client) {
    String username = "";
    try {
      username = client.currentUsername();
    }
    catch (Exception ignored) {
      // 取用户名失败（网络/权限）时退化为只按服务器缓存
    }
    return client.serverUrl() + "|" + username;
  }

  private static Caps introspect(GitLabClient client) {
    try {
      JsonObject data = client.execute(INTROSPECTION, null);
      Set<String> mr = fieldNames(JsonSupport.obj(data, "mr"));
      Set<String> perm = fieldNames(JsonSupport.obj(data, "perm"));
      if (mr.isEmpty()) {
        return fallback();
      }
      if (perm.isEmpty()) {
        perm = MINIMAL_PERM;
      }
      return new Caps(mr, perm, true);
    }
    catch (Throwable t) {
      return fallback();
    }
  }

  private static Set<String> fieldNames(JsonObject typeObject) {
    Set<String> names = new LinkedHashSet<>();
    JsonArray fields = JsonSupport.arr(typeObject, "fields");
    if (fields == null) {
      return names;
    }
    for (JsonElement element : fields) {
      if (element != null && element.isJsonObject()) {
        String name = JsonSupport.string(element.getAsJsonObject(), "name", "");
        if (!name.isBlank()) {
          names.add(name);
        }
      }
    }
    return names;
  }

  private static Caps fallback() {
    Set<String> mr = new LinkedHashSet<>(MINIMAL_MR);
    return new Caps(mr, MINIMAL_PERM, false);
  }

  // ------------------------------------------------------------------ 查询构造

  public static String listQuery(Caps caps) {
    StringBuilder sb = new StringBuilder();
    sb.append("query($projectId: ID!, $state: MergeRequestState, $pageSize: Int = 50, $cursor: String) {\n");
    sb.append("  project(fullPath: $projectId) {\n");
    sb.append("    mergeRequests(state: $state, after: $cursor, first: $pageSize) {\n");
    sb.append("      nodes {\n");
    for (String field : List.of("iid", "title", "sourceBranch", "targetBranch", "state", "webUrl", "mergeable", "draft")) {
      if ("draft".equals(field) && !caps.mr("draft")) {
        continue;
      }
      if (!"draft".equals(field) && !caps.mr(field)) {
        continue;
      }
      sb.append("        ").append(field).append("\n");
    }
    sb.append("      }\n");
    sb.append("      pageInfo { hasNextPage endCursor }\n");
    sb.append("    }\n");
    sb.append("  }\n");
    sb.append("}");
    return sb.toString();
  }

  public static String detailsQuery(Caps caps) {
    StringBuilder sb = new StringBuilder();
    sb.append("query($projectId: ID!, $iid: String!) {\n");
    sb.append("  project(fullPath: $projectId) {\n");
    sb.append("    mergeRequest(iid: $iid) {\n");
    for (String field : List.of("iid", "title", "state", "webUrl", "mergeable", "sourceBranch", "targetBranch")) {
      if (caps.mr(field)) {
        sb.append("      ").append(field).append("\n");
      }
    }
    for (String field : DETAIL_CANDIDATES) {
      if (caps.mr(field)) {
        sb.append("      ").append(field).append(lineFor(field, caps)).append("\n");
      }
    }
    sb.append("    }\n");
    sb.append("  }\n");
    sb.append("}");
    return sb.toString();
  }

  private static String lineFor(String field, Caps caps) {
    return switch (field) {
      case "author" -> " { username name }";
      case "assignees", "reviewers", "approvedBy" -> " { nodes { username name } }";
      case "labels" -> " { nodes { title color } }";
      case "userPermissions" -> permissionsSubSelection(caps);
      case "headPipeline" -> " { status }";
      default -> "";
    };
  }

  private static String permissionsSubSelection(Caps caps) {
    StringBuilder sb = new StringBuilder(" { ");
    for (String perm : List.of("canMerge", "canApprove", "createNote", "updateMergeRequest")) {
      if (caps.perm(perm)) {
        sb.append(perm).append(' ');
      }
    }
    return sb.append('}').toString();
  }

  /** 供自测与诊断：报告当前能力来源。 */
  public static String describe(Caps caps) {
    return caps.introspected()
        ? "introspection（MergeRequest " + caps.mergeRequest().size() + " 字段）"
        : "最小回退集（introspection 不可用）";
  }
}
