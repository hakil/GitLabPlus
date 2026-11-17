package com.hakil.gitlabplus.service;

import com.hakil.gitlabplus.api.GitLabClient;
import com.hakil.gitlabplus.api.JsonSupport;
import com.hakil.gitlabplus.api.RestPaths;
import com.hakil.gitlabplus.model.Discussion;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 评审能力（P2）：讨论/评论、resolve、reactions、草稿评论、发布草稿。
 *
 * <p>全部走 GitLab REST v4，端点形状与官方插件一致（官方在这些能力上也用 REST）。</p>
 */
public final class GitLabReviewService {

  private final GitLabClient client;

  public GitLabReviewService(GitLabClient client) {
    this.client = client;
  }

  // ------------------------------------------------------------------ 讨论

  public List<Discussion> listDiscussions(String projectPath, String iid) {
    JsonElement response = client.restJsonElement("GET", RestPaths.discussions(projectPath, iid), null);
    List<Discussion> result = new ArrayList<>();
    if (response != null && response.isJsonArray()) {
      JsonArray array = response.getAsJsonArray();
      for (JsonElement element : array) {
        if (element != null && element.isJsonObject()) {
          result.add(Discussion.from(element.getAsJsonObject()));
        }
      }
    }
    return result;
  }

  /** 新建讨论（顶层评论）。 */
  public JsonObject addDiscussion(String projectPath, String iid, String body) {
    return client.restPost(RestPaths.discussions(projectPath, iid), bodyPayload(body));
  }

  /** 在既有讨论内回复。 */
  public JsonObject reply(String projectPath, String iid, String discussionId, String body) {
    return client.restPost(RestPaths.discussionNotes(projectPath, iid, discussionId), bodyPayload(body));
  }

  /**
   * 解决 / 取消解决讨论。
   *
   * <p>GitLab 要求 {@code resolved} 作为 <b>query 参数</b>（{@code ?resolved=true}），
   * 放在 JSON body 里会被忽略并报 400 —— 这是之前 Resolve/Unresolve 报错的根因。</p>
   */
  public void resolve(String projectPath, String iid, String discussionId, boolean resolved) {
    client.restPutQuery(RestPaths.discussion(projectPath, iid, discussionId),
                        java.util.Map.of("resolved", String.valueOf(resolved)));
  }

  public JsonObject updateNote(String projectPath, String iid, String noteId, String body) {
    return client.restPut(RestPaths.note(projectPath, iid, noteId), bodyPayload(body));
  }

  public void deleteNote(String projectPath, String iid, String noteId) {
    client.restDelete(RestPaths.note(projectPath, iid, noteId));
  }

  // ------------------------------------------------------------------ reactions

  /** 当前 token 的用户名（点赞 toggle 需要判断是否自己点过）。 */
  public String currentUsername() {
    return client.currentUsername();
  }

  /**
   * 查某条 note 的全部 reaction。
   *
   * <p><b>关键</b>：REST 的 discussions / notes 响应里通常<b>不包含</b>
   * {@code award_emoji} 字段，所以不能拿 note 上的字段判断“我是否已点过”，
   * 必须单独请求此端点。这正是之前重复点赞仍报错的原因。</p>
   */
  public List<Discussion.Note.Award> listAwards(String projectPath, String iid, String noteId) {
    JsonElement response = client.restJsonElement("GET", RestPaths.awardEmoji(projectPath, iid, noteId), null);
    List<Discussion.Note.Award> result = new ArrayList<>();
    if (response != null && response.isJsonArray()) {
      for (JsonElement element : response.getAsJsonArray()) {
        if (element != null && element.isJsonObject()) {
          JsonObject node = element.getAsJsonObject();
          result.add(new Discussion.Note.Award(
              JsonSupport.string(node, "id", ""),
              JsonSupport.string(node, "name", ""),
              JsonSupport.string(JsonSupport.obj(node, "user"), "username", "")));
        }
      }
    }
    return result;
  }

  /**
   * 添加 reaction。
   *
   * <p>{@code name} 必须是 query 参数（{@code ?name=thumbsup}），body 无效 ——
   * 这是之前点赞报错的根因。</p>
   */
  public void addAward(String projectPath, String iid, String noteId, String emoji) {
    client.restPostQuery(RestPaths.awardEmoji(projectPath, iid, noteId),
                         java.util.Map.of("name", emoji));
  }

  public void removeAward(String projectPath, String iid, String noteId, String awardId) {
    client.restDelete(RestPaths.awardEmoji(projectPath, iid, noteId, awardId));
  }

  /** notes / discussions 的提交参数（字段名是 body，区别于草稿的 note）。 */
  private static JsonObject bodyPayload(String body) {
    JsonObject payload = new JsonObject();
    payload.addProperty("body", body == null ? "" : body);
    return payload;
  }
}
