package com.caidailian.gitlabplus.service;

import com.caidailian.gitlabplus.api.GitLabClient;
import com.caidailian.gitlabplus.api.JsonSupport;
import com.caidailian.gitlabplus.api.RestPaths;
import com.caidailian.gitlabplus.i18n.GitLabPlusBundle;
import com.caidailian.gitlabplus.model.ChangesPage;
import com.caidailian.gitlabplus.model.DiffParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Diff 评审（P3）：取变更、发行内评论。
 *
 * <p>行内评论的 {@code position} 必须走 <b>query 参数</b>（官方
 * {@code addDiffNote} 即 {@code POST /discussions?body=...&position[...]=...}），
 * 不是 JSON body。</p>
 */
public final class GitLabDiffService {

  private final GitLabClient client;

  public GitLabDiffService(GitLabClient client) {
    this.client = client;
  }

  /**
   * 取一个 MR 的变更快照（含 diff_refs 三元素 sha）。
   *
   * <p>{@code /changes} 的 {@code diff_refs} 个别情况下不完整（如极旧版本的 MR、
   * 无共同祖先的分支），而它又是行内评论 position 的必要参数。此时回退到
   * {@code /versions} 取最近一条带完整三元 sha 的版本并合并 ——
   * 否则 UI 上的「评论选中行/建议修改」会永久置灰且没有解释。</p>
   */
  public ChangesPage loadChanges(String projectPath, String iid) {
    JsonObject response = client.restGet(RestPaths.changes(projectPath, iid));
    ChangesPage page = ChangesPage.from(response);
    if (page.diffRefs() != null && page.diffRefs().complete()) {
      return page;
    }
    ChangesPage.DiffRefs merged = ChangesPage.DiffRefs.merge(page.diffRefs(), latestVersionRefs(projectPath, iid));
    return new ChangesPage(merged, page.files());
  }

  /**
   * 从 {@code /merge_requests/:iid/versions} 取最近一条完整的三元 sha。
   *
   * @return 取不到时返回 null（调用方保持原值，UI 会提示原因）
   */
  public ChangesPage.DiffRefs latestVersionRefs(String projectPath, String iid) {
    try {
      JsonElement element = client.restJsonElement(
          "GET", RestPaths.mergeRequestVersions(projectPath, iid), null);
      if (element == null || !element.isJsonArray()) {
        return null;
      }
      JsonArray versions = element.getAsJsonArray();
      for (JsonElement item : versions) {
        if (item == null || !item.isJsonObject()) {
          continue;
        }
        JsonObject node = item.getAsJsonObject();
        ChangesPage.DiffRefs refs = new ChangesPage.DiffRefs(
            JsonSupport.string(node, "base_commit_sha", ""),
            JsonSupport.string(node, "start_commit_sha", ""),
            JsonSupport.string(node, "head_commit_sha", ""));
        if (refs.complete()) {
          return refs;
        }
      }
    }
    catch (RuntimeException e) {
      // versions 端点不可用（权限/版本）时不影响主链路
      return null;
    }
    return null;
  }

  /** 取仓库文件在指定 ref 下的原始文本（原生 diff 用；文件不存在返回空串）。 */
  public String rawFile(String projectPath, String filePath, String ref) {
    if (filePath == null || filePath.isBlank() || ref == null || ref.isBlank()) {
      return "";
    }
    try {
      return client.restGetText(RestPaths.repositoryRawFile(projectPath, filePath, ref));
    }
    catch (RuntimeException e) {
      // 新增文件在 base 下不存在（404）、删除文件在 head 下不存在：都按空内容处理
      return "";
    }
  }

  /**
   * 发行内评论。
   *
   * @throws GitLabClient.GitLabApiException position 不完整时直接失败（fail-fast）
   */
  public JsonObject addDiffNote(String projectPath,
                                String iid,
                                String body,
                                ChangesPage.DiffRefs refs,
                                DiffParser.FileDiff file,
                                DiffParser.DiffLine line) {
    if (refs == null || !refs.complete()) {
      throw new GitLabClient.GitLabApiException(GitLabPlusBundle.message("error.diffRefsMissing"));
    }
    if (file == null || line == null) {
      throw new GitLabClient.GitLabApiException("缺少文件或行定位信息");
    }
    Map<String, String> params = positionParams(refs, file, line);
    params.put("body", body == null ? "" : body);
    return client.restPostQuery(RestPaths.discussions(projectPath, iid), params);
  }

  /**
   * 构造 position 查询参数（纯函数，便于单测）。
   *
   * <p>GitLab 语义：新增行只传 new_line，删除行只传 old_line，上下文行两者都传。</p>
   */
  public static Map<String, String> positionParams(ChangesPage.DiffRefs refs,
                                                   DiffParser.FileDiff file,
                                                   DiffParser.DiffLine line) {
    if (refs == null || !refs.complete()) {
      throw new GitLabClient.GitLabApiException(GitLabPlusBundle.message("error.diffRefsMissing"));
    }
    Map<String, String> params = new LinkedHashMap<>();
    params.put("position[base_sha]", refs.baseSha());
    params.put("position[start_sha]", refs.startSha());
    params.put("position[head_sha]", refs.headSha());
    params.put("position[position_type]", "text");
    params.put("position[new_path]", file.newPath());
    params.put("position[old_path]", file.oldPath());
    if (line.newLine() > 0 && line.oldLine() > 0) {
      params.put("position[new_line]", String.valueOf(line.newLine()));
      params.put("position[old_line]", String.valueOf(line.oldLine()));
    }
    else if (line.newLine() > 0) {
      params.put("position[new_line]", String.valueOf(line.newLine()));
    }
    else if (line.oldLine() > 0) {
      params.put("position[old_line]", String.valueOf(line.oldLine()));
    }
    else {
      throw new GitLabClient.GitLabApiException(GitLabPlusBundle.message("error.noPosition"));
    }
    return params;
  }

  /** 解析一行 diff 所属的文件（按 new_path / old_path 匹配）。 */
  public static DiffParser.FileDiff findFile(ChangesPage page, String path) {
    if (page == null || path == null) {
      return null;
    }
    for (DiffParser.FileDiff file : page.files()) {
      if (path.equals(file.newPath()) || path.equals(file.oldPath())) {
        return file;
      }
    }
    return null;
  }

  /** 校验响应是否包含 expected 字段（用于契约自检）。 */
  public static boolean hasDiffRefs(ChangesPage page) {
    return page != null && page.diffRefs() != null && page.diffRefs().complete();
  }

  /** 便捷：取 changes 并校验 diff_refs 完整性。 */
  public ChangesPage loadChangesChecked(String projectPath, String iid) {
    ChangesPage page = loadChanges(projectPath, iid);
    if (!hasDiffRefs(page)) {
      throw new GitLabClient.GitLabApiException(
          "GitLab 未返回完整的 diff_refs：" + JsonSupport.abbreviate(String.valueOf(page == null ? "null" : "partial"), 80));
    }
    return page;
  }
}
