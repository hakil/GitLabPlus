package test;

import org.junit.jupiter.api.Test;
import com.hakil.gitlabplus.api.MergeRequestCapabilities;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 版本自适应的查询构造自测。
 *
 * <p>回归两次真实故障：<br>
 * 1. {@code DiffRefs.headSha} 非空字段在无 diff 的 MR 上返回 null → 整个查询失败；<br>
 * 2. 老版本 {@code MergeRequest} 没有 {@code squashReadOnly} 字段 → 查询被拒。<br>
 * 因此：任何查询都不得包含 diffRefs；字段必须按 introspection 结果裁剪。</p>
 */
class CapabilitiesTest extends SuiteTestSupport {

    @Test
  void suite() {
    // 老版本：没有 squashReadOnly / forceRemoveSourceBranch / 权限字段只有 canMerge
    Set<String> oldMr = new LinkedHashSet<>(Set.of(
        "iid", "title", "description", "state", "webUrl", "draft", "mergeable", "conflicts",
        "sourceBranch", "targetBranch", "shouldRemoveSourceBranch", "squashOnMerge",
        "author", "assignees", "labels", "userPermissions", "headPipeline"));
    MergeRequestCapabilities.Caps oldCaps =
        new MergeRequestCapabilities.Caps(oldMr, Set.of("canMerge"), true);

    String oldDetails = MergeRequestCapabilities.detailsQuery(oldCaps);
    TestAssert.eq("老版本不含 squashReadOnly", false, oldDetails.contains("squashReadOnly"));
    TestAssert.eq("老版本含 shouldRemoveSourceBranch", true, oldDetails.contains("shouldRemoveSourceBranch"));
    TestAssert.eq("老版本权限只请求 canMerge", false, oldDetails.contains("canApprove"));
    TestAssert.eq("老版本含 canMerge", true, oldDetails.contains("canMerge"));
    TestAssert.eq("详情不含 diffRefs", false, oldDetails.contains("diffRefs"));

    // 新版本：字段齐全
    Set<String> newMr = new LinkedHashSet<>(oldMr);
    newMr.add("squashReadOnly");
    newMr.add("forceRemoveSourceBranch");
    newMr.add("defaultMergeCommitMessage");
    newMr.add("createdAt");
    MergeRequestCapabilities.Caps newCaps =
        new MergeRequestCapabilities.Caps(newMr,
                                          Set.of("canMerge", "canApprove", "createNote", "updateMergeRequest"),
                                          true);
    String newDetails = MergeRequestCapabilities.detailsQuery(newCaps);
    TestAssert.eq("新版本含 squashReadOnly", true, newDetails.contains("squashReadOnly"));
    TestAssert.eq("新版本含 canApprove", true, newDetails.contains("canApprove"));
    TestAssert.eq("新版本含默认合并信息", true, newDetails.contains("defaultMergeCommitMessage"));
    TestAssert.eq("新版本仍不含 diffRefs", false, newDetails.contains("diffRefs"));

    // 列表查询按 caps 裁剪
    String oldList = MergeRequestCapabilities.listQuery(oldCaps);
    TestAssert.eq("列表含 iid", true, oldList.contains("iid"));
    TestAssert.eq("列表不含 diffRefs", false, oldList.contains("diffRefs"));
    TestAssert.eq("列表含 pageInfo", true, oldList.contains("pageInfo"));

    Set<String> noDraft = new LinkedHashSet<>(Set.of("iid", "title", "state"));
    String minimalList = MergeRequestCapabilities.listQuery(
        new MergeRequestCapabilities.Caps(noDraft, Set.of(), false));
    TestAssert.eq("无 draft 时不请求 draft", false, minimalList.contains("draft"));
    TestAssert.eq("仅请求已有字段", true, minimalList.contains("iid") && !minimalList.contains("sourceBranch"));

    // 查询文本结构平衡（大括号成对），避免拼装漏闭合
    TestAssert.eq("列表大括号平衡", count(oldList, '{'), count(oldList, '}'));
    TestAssert.eq("详情大括号平衡", count(newDetails, '{'), count(newDetails, '}'));

    TestAssert.eq("introspection 描述", "introspection", MergeRequestCapabilities.describe(newCaps).contains("introspection") ? "introspection" : "other");
    TestAssert.eq("回退集描述", "最小回退集",
                      MergeRequestCapabilities.describe(
                          new MergeRequestCapabilities.Caps(Set.of("iid"), Set.of(), false)).contains("最小回退集")
                          ? "最小回退集" : "other");
  }

  private static int count(String text, char c) {
    int n = 0;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == c) {
        n++;
      }
    }
    return n;
  }
}
