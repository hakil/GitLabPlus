package com.hakil.gitlabplus.service;

import com.hakil.gitlabplus.api.GitLabClient;
import com.hakil.gitlabplus.i18n.GitLabPlusBundle;import com.hakil.gitlabplus.api.JsonSupport;
import com.hakil.gitlabplus.api.RestPaths;
import com.hakil.gitlabplus.model.ChangesPage;
import com.hakil.gitlabplus.model.Discussion;
import com.hakil.gitlabplus.model.MergeRequestInfo;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 只读自检：一次性验证本插件依赖的全部服务端契约。
 *
 * <p>设计目的：真实环境验收时不必逐个按钮点击 —— 先跑自检确认
 * token / GraphQL / REST / diff_refs / discussions 五条链路都通，
 * 再去做写操作（合并、评论）。自检**不产生任何写操作**。</p>
 */
public final class SelfCheckService {

  private SelfCheckService() {
  }

  public record Step(String name, boolean ok, String detail) {
    @Override
    public String toString() {
      return (ok ? "✓ " : "✗ ") + name + " — " + detail;
    }
  }

  /**
   * 依次执行只读检查。
   *
   * @param projectPath 项目全路径
   * @return 检查步骤与结果（顺序固定，失败不中断后续检查）
   */
  public static List<Step> run(GitLabClient client, String projectPath) {
    List<Step> steps = new ArrayList<>();

    // 1. token 有效性（REST）
    String username = null;
    try {
      JsonObject user = client.restGet("/api/v4/user");
      username = JsonSupport.string(user, "username", "");
      steps.add(new Step(GitLabPlusBundle.message("check.token"), !username.isBlank(),
                         username.isBlank() ? "返回空 username（token 可能无 api scope）" : "用户 " + username));
    }
    catch (Throwable t) {
      steps.add(new Step(GitLabPlusBundle.message("check.token"), false, message(t)));
      return steps;
    }

    // 2. 项目可见性（REST）
    try {
      JsonObject project = client.restGet("/api/v4/projects/" + RestPaths.encode(projectPath));
      String fullPath = JsonSupport.string(project, "path_with_namespace", "");
      steps.add(new Step(GitLabPlusBundle.message("check.project"), !fullPath.isBlank(),
                         fullPath.isBlank() ? "未返回 path_with_namespace" : fullPath));
    }
    catch (Throwable t) {
      steps.add(new Step(GitLabPlusBundle.message("check.project"), false, message(t)));
    }

    // 3. GraphQL 列表（opened）
    GitLabService service = new GitLabService(client);
    List<MergeRequestInfo> mergeRequests = List.of();
    try {
      GitLabService.Page page = service.listMergeRequests(projectPath, "opened", null);
      mergeRequests = page.items();
      steps.add(new Step(GitLabPlusBundle.message("check.graphql"), true,
                         "opened " + mergeRequests.size() + " 条"
                             + (page.hasNextPage() ? "（还有更多页）" : "")));
    }
    catch (Throwable t) {
      steps.add(new Step(GitLabPlusBundle.message("check.graphql"), false, message(t)));
    }

    // 只读端点需要至少一个 MR：无 open MR 时回退到任意状态的最近一个 MR
    String iid = null;
    if (!mergeRequests.isEmpty()) {
      iid = mergeRequests.get(0).iid;
    }
    else {
      try {
        GitLabService.Page any = service.listMergeRequests(projectPath, null, null);
        if (!any.items().isEmpty()) {
          iid = any.items().get(0).iid;
          steps.add(new Step(GitLabPlusBundle.message("check.probe"), true,
                             "项目无 open MR，改用最近一个 MR !" + iid
                                 + "（状态 " + any.items().get(0).state + "）验证只读端点"));
        }
      }
      catch (Throwable t) {
        steps.add(new Step(GitLabPlusBundle.message("check.probe"), false, message(t)));
      }
    }

    if (iid == null) {
      steps.add(new Step("REST changes", false, GitLabPlusBundle.message("check.skipNoMr")));
      steps.add(new Step(GitLabPlusBundle.message("check.discussions"), false, GitLabPlusBundle.message("check.skipNoMr")));
      steps.add(new Step(GitLabPlusBundle.message("check.approvals"), false, GitLabPlusBundle.message("check.skipNoMr")));
      steps.add(new Step(GitLabPlusBundle.message("check.award"), false, GitLabPlusBundle.message("check.skipNoMr")));
      return steps;
    }

    // 4. changes + diff_refs（行内评论的前提）
    try {
      GitLabDiffService diffService = new GitLabDiffService(client);
      ChangesPage page = diffService.loadChanges(projectPath, iid);
      boolean refsOk = page.diffRefs().complete();
      steps.add(new Step(GitLabPlusBundle.message("check.changes"),
                         refsOk,
                         "!" + iid + " 文件 " + page.files().size()
                             + "，+/- " + page.totalAdded() + "/" + page.totalRemoved()
                             + (refsOk ? "，diff_refs 完整" : "，diff_refs 缺失（行内评论不可用）")));
    }
    catch (Throwable t) {
      steps.add(new Step(GitLabPlusBundle.message("check.changes"), false, message(t)));
    }

    // 5. discussions（评审能力）
    try {
      GitLabReviewService reviewService = new GitLabReviewService(client);
      List<Discussion> discussions = reviewService.listDiscussions(projectPath, iid);
      steps.add(new Step(GitLabPlusBundle.message("check.discussions"), true,
                         "!" + iid + " 讨论 " + discussions.size() + " 条"));
    }
    catch (Throwable t) {
      steps.add(new Step(GitLabPlusBundle.message("check.discussions"), false, message(t)));
    }

    // 6. approvals（撤回批准的前提：user_has_approved）
    try {
      boolean mine = service.userHasApproved(projectPath, iid);
      steps.add(new Step(GitLabPlusBundle.message("check.approvals"), true, "!" + iid + " user_has_approved=" + mine));
    }
    catch (Throwable t) {
      steps.add(new Step(GitLabPlusBundle.message("check.approvals"), false, message(t)));
    }

    // 7. reactions（点赞前提）：对比 discussions 内联字段与独立端点
    try {
      GitLabReviewService reviewService = new GitLabReviewService(client);
      List<Discussion> discussions = reviewService.listDiscussions(projectPath, iid);
      Discussion.Note target = null;
      int inlineAwards = 0;
      for (Discussion discussion : discussions) {
        for (Discussion.Note note : discussion.notes) {
          if (!note.system && note.id != null && !note.id.isBlank()) {
            target = note;
            inlineAwards = note.awards.size();
            break;
          }
        }
        if (target != null) {
          break;
        }
      }
      if (target == null) {
        steps.add(new Step(GitLabPlusBundle.message("check.award"), false, GitLabPlusBundle.message("check.skipNoComment")));
      }
      else {
        List<Discussion.Note.Award> awards = reviewService.listAwards(projectPath, iid, target.id);
        String me = client.currentUsername();
        boolean mine = awards.stream()
            .anyMatch(award -> "thumbsup".equals(award.name()) && me.equals(award.username()));
        steps.add(new Step(GitLabPlusBundle.message("check.award"), true,
                           "note " + target.id + " 独立端点 reaction " + awards.size()
                               + "（discussions 内联 award_emoji " + inlineAwards + "）"
                               + "，我已点 thumbsup=" + mine));
      }
    }
    catch (Throwable t) {
      steps.add(new Step(GitLabPlusBundle.message("check.award"), false, message(t)));
    }

    return steps;
  }

  /** 报告文本（可直接展示给用户）。 */
  public static String report(List<Step> steps) {
    StringBuilder sb = new StringBuilder();
    int ok = 0;
    for (Step step : steps) {
      if (step.ok()) {
        ok++;
      }
      sb.append(step).append('\n');
    }
    sb.append('\n').append(ok).append('/').append(steps.size()).append(" 项通过");
    if (ok < steps.size()) {
      sb.append(GitLabPlusBundle.message("check.footer"));
    }
    return sb.toString();
  }

  private static String message(Throwable t) {
    Throwable cur = t;
    while (cur.getCause() != null && cur.getCause() != cur) {
      cur = cur.getCause();
    }
    String message = cur.getMessage();
    return message == null || message.isBlank() ? cur.getClass().getSimpleName() : message;
  }
}
