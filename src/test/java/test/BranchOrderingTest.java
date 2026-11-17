package test;

import org.junit.jupiter.api.Test;
import com.hakil.gitlabplus.model.BranchOrdering;

import org.junit.jupiter.api.Test;
import java.util.List;

/**
 * 新建 MR 的分支排序与默认目标推荐自测。
 */
class BranchOrderingTest extends SuiteTestSupport {

    @Test
  void suite() {
    // ---- 源分支：当前分支置顶，其余保持原序 ----
    TestAssert.eq("当前分支置顶", List.of("feature/x", "dev", "aaa"),
                      BranchOrdering.orderSources(List.of("dev", "aaa", "feature/x"), "feature/x"));
    TestAssert.eq("无当前分支保持原序", List.of("dev", "aaa"),
                      BranchOrdering.orderSources(List.of("dev", "aaa"), ""));
    TestAssert.eq("null 候选不抛", List.of(),
                      BranchOrdering.orderSources(null, "dev"));

    // ---- 场景推荐 ----
    TestAssert.eq("feature → dev", List.of("dev", "develop", "development"),
                      BranchOrdering.suggestedTargets("feature/login"));
    TestAssert.eq("feat → dev", "dev", BranchOrdering.suggestedTargets("feat/x").get(0));
    TestAssert.eq("hotfix → master", List.of("master", "main", "prod", "production"),
                      BranchOrdering.suggestedTargets("hotfix/urgent"));
    TestAssert.eq("release → master", "master",
                      BranchOrdering.suggestedTargets("release/1.2.0").get(0));
    TestAssert.eq("bugfix → dev", "dev", BranchOrdering.suggestedTargets("bugfix/login").get(0));
    TestAssert.eq("普通分支默认含 dev", "dev", BranchOrdering.suggestedTargets("misc").get(0));
    TestAssert.eq("null 分支不抛", "dev", BranchOrdering.suggestedTargets(null).get(0));
    TestAssert.eq("大小写不敏感", "master", BranchOrdering.suggestedTargets("HOTFIX/x").get(0));

    // ---- 目标分支排序：建议 → 上次使用 → 主干 → 其余 ----
    List<String> candidates = List.of("master", "main", "dev", "feature/old", "hotfix/tmp");
    TestAssert.eq("feature 场景 dev 置顶", "dev",
                      BranchOrdering.orderTargets(candidates, "main", "feature/login").get(0));
    TestAssert.eq("hotfix 场景 master 置顶", "master",
                      BranchOrdering.orderTargets(candidates, "dev", "hotfix/x").get(0));

    // ---- 默认目标的选择 ----
    TestAssert.eq("建议优先于上次使用", "dev",
                      BranchOrdering.pickTarget(List.of("master", "dev"), "master", "feature/x"));
    TestAssert.eq("无建议匹配时用上次使用", "release/2",
                      BranchOrdering.pickTarget(List.of("release/2", "other"), "release/2", "misc"));
    TestAssert.eq("都没有则取第一个", "aaa",
                      BranchOrdering.pickTarget(List.of("aaa", "bbb"), "zzz", "misc"));
    TestAssert.eq("空列表返回空串", "",
                      BranchOrdering.pickTarget(List.of(), "dev", "feature/x"));
    TestAssert.eq("上次使用为空不崩", "dev",
                      BranchOrdering.pickTarget(List.of("master", "dev"), null, "feature/x"));
  }
}
