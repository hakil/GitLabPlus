package test;

import org.junit.jupiter.api.Test;
import com.caidailian.gitlabplus.api.RestPaths;
import org.junit.jupiter.api.Test;
import com.caidailian.gitlabplus.model.Discussion;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;

/**
 * P2 基础设施自测：REST 讨论解析 + 路径构造。
 *
 * <p>样例 JSON 取自 GitLab REST v4 讨论响应的实际字段形状
 * （{@code GET /projects/:id/merge_requests/:iid/discussions}）。</p>
 */
class DiscussionParseTest extends SuiteTestSupport {

    @Test
  void suite() {
    String discussionJson = """
        {
          "id": "6d9d9b1e8c2a",
          "individual_note": false,
          "notes": [
            {
              "id": 101,
              "body": "LGTM",
              "author": {"username": "lishiqi", "name": "Li Shiqi"},
              "system": false,
              "created_at": "2026-01-01T10:00:00Z",
              "resolvable": true,
              "resolved": false,
              "award_emoji": [
                {"id": 9, "name": "thumbsup"},
                {"id": 10, "name": "thumbsup"},
                {"id": 11, "name": "eyes"}
              ]
            },
            {
              "id": 102,
              "body": "同意",
              "author": {"username": "zhangsan", "name": "Zhang San"},
              "system": false,
              "created_at": "2026-01-01T11:00:00Z",
              "resolvable": false,
              "resolved": false
            }
          ]
        }
        """;

    Discussion discussion = Discussion.from(obj(discussionJson));
    TestAssert.eq("discussion id", "6d9d9b1e8c2a", discussion.id);
    TestAssert.eq("notes 数量", 2, discussion.notes.size());
    TestAssert.eq("首条作者", "lishiqi", discussion.firstAuthor());
    TestAssert.eq("首条正文", "LGTM", discussion.firstBody());
    TestAssert.eq("resolvable 推导", true, discussion.resolvable());
    TestAssert.eq("resolved 推导", false, discussion.resolved());
    TestAssert.eq("reaction 汇总", "thumbsup 2 eyes 1", discussion.notes.get(0).awardSummary());
    TestAssert.eq("system 标记", false, discussion.notes.get(0).system);
    TestAssert.eq("系统事件识别", true,
                      Discussion.from(obj("{\"notes\":[{\"id\":1,\"system\":true}]}"))
                          .notes.get(0).system);
    TestAssert.eq("无 notes 不越界", "", Discussion.from(obj("{\"id\":\"x\"}")).firstAuthor());

    TestAssert.eq("项目路径编码", "a%2Fb", RestPaths.encode("a/b"));
    TestAssert.eq("discussions 路径",
                      "/api/v4/projects/g%2Fp/merge_requests/7/discussions",
                      RestPaths.discussions("g/p", "7"));
    TestAssert.eq("回复路径",
                      "/api/v4/projects/g%2Fp/merge_requests/7/discussions/abc/notes",
                      RestPaths.discussionNotes("g/p", "7", "abc"));
    TestAssert.eq("resolve 路径",
                      "/api/v4/projects/g%2Fp/merge_requests/7/discussions/abc",
                      RestPaths.discussion("g/p", "7", "abc"));
    TestAssert.eq("reaction 删除路径",
                      "/api/v4/projects/g%2Fp/merge_requests/7/notes/9/award_emoji/3",
                      RestPaths.awardEmoji("g/p", "7", "9", "3"));

    // 合并后流水线需要按目标分支 / merge sha 查
    TestAssert.eq("pipelines by ref",
                      "/api/v4/projects/g%2Fp/pipelines?ref=dev&per_page=20&order_by=id&sort=desc",
                      RestPaths.pipelinesByRef("g/p", "dev", 20));
    TestAssert.eq("pipelines by sha",
                      "/api/v4/projects/g%2Fp/pipelines?sha=abc123&per_page=20&order_by=id&sort=desc",
                      RestPaths.pipelinesBySha("g/p", "abc123", 20));
    TestAssert.eq("job retry 路径",
                      "/api/v4/projects/g%2Fp/jobs/9/retry",
                      RestPaths.jobRetry("g/p", "9"));
    TestAssert.eq("job cancel 路径",
                      "/api/v4/projects/g%2Fp/jobs/9/cancel",
                      RestPaths.jobCancel("g/p", "9"));
    TestAssert.eq("pipeline retry 路径",
                      "/api/v4/projects/g%2Fp/pipelines/5/retry",
                      RestPaths.pipelineRetry("g/p", "5"));
  }

  private static JsonObject obj(String json) {
    return JsonParser.parseString(json).getAsJsonObject();
  }
}
