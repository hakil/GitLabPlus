package test;

import org.junit.jupiter.api.Test;
import com.caidailian.gitlabplus.model.PipelineInfo;
import org.junit.jupiter.api.Test;
import com.caidailian.gitlabplus.model.PipelineRole;

/**
 * 流水线角色判定自测。
 *
 * <p>目标：让表格里一眼区分「合并结果 / 目标分支 / MR 校验 / 源分支」，
 * 而不是只能看到 GitLab 原始的 push / merge_request_event。</p>
 */
class PipelineRoleTest extends SuiteTestSupport {

  private static final String SRC = "feature/login";
  private static final String TGT = "dev";
  private static final String MERGE_SHA = "abcdef1234567890abcdef1234567890abcdef12";

    @Test
  void suite() {
    TestAssert.eq("sha 命中合并提交 -> 合并结果", PipelineRole.Kind.MERGE_RESULT,
                      PipelineRole.classify(pipe("1", "success", TGT, MERGE_SHA, "push"),
                                            SRC, TGT, MERGE_SHA));
    TestAssert.eq("合并结果来源列", "合并",
                      PipelineRole.shortLabel(
                          pipe("1", "success", TGT, MERGE_SHA, "push"), SRC, TGT, MERGE_SHA));

    TestAssert.eq("ref=目标分支（非 merge sha）-> 目标分支", PipelineRole.Kind.TARGET_BRANCH,
                      PipelineRole.classify(pipe("2", "failed", TGT, "1111111111111111111111111111111111111111", "push"),
                                            SRC, TGT, MERGE_SHA));

    TestAssert.eq("ref=源分支 + MR 事件 -> MR 校验", PipelineRole.Kind.SOURCE_MR,
                      PipelineRole.classify(pipe("3", "success", SRC, "2222222222222222222222222222222222222222",
                                                 "merge_request_event"), SRC, TGT, ""));
    TestAssert.eq("ref=源分支 + push -> 源分支", PipelineRole.Kind.SOURCE_PUSH,
                      PipelineRole.classify(pipe("4", "running", SRC, "3333333333333333333333333333333333333333", "push"),
                                            SRC, TGT, ""));
    TestAssert.eq("定时触发", PipelineRole.Kind.SCHEDULE,
                      PipelineRole.classify(pipe("5", "success", "main", "4444444444444444444444444444444444444444",
                                                 "schedule"), SRC, TGT, ""));
    TestAssert.eq("无关分支 -> 其他", PipelineRole.Kind.OTHER,
                      PipelineRole.classify(pipe("6", "success", "other", "5555555555555555555555555555555555555555",
                                                 "web"), SRC, TGT, ""));
    TestAssert.eq("其他角色回退到 source 文案", "网页",
                      PipelineRole.shortLabel(pipe("6", "success", "other", "x", "web"), SRC, TGT, ""));
    TestAssert.eq("空流水线 -> 其他", PipelineRole.Kind.OTHER,
                      PipelineRole.classify(null, SRC, TGT, MERGE_SHA));

    TestAssert.eq("merge sha 大小写不敏感", PipelineRole.Kind.MERGE_RESULT,
                      PipelineRole.classify(pipe("7", "success", TGT, MERGE_SHA.toUpperCase(), "push"),
                                            SRC, TGT, MERGE_SHA));

    TestAssert.eq("未合并时 ref=目标分支仍可识别", PipelineRole.Kind.TARGET_BRANCH,
                      PipelineRole.classify(pipe("8", "success", TGT, "6666666666666666666666666666666666666666", "push"),
                                            SRC, TGT, ""));

    TestAssert.eq("分支名精确匹配（非前缀）", PipelineRole.Kind.OTHER,
                      PipelineRole.classify(pipe("9", "success", "dev2", "7777777777777777777777777777777777777777", "push"),
                                            SRC, TGT, ""));

    TestAssert.eq("Kind 描述存在", true,
                      PipelineRole.Kind.MERGE_RESULT.getDescription().contains("合并"));

    // 「触发」列的箭头表达式：左 = 跑在哪个分支，右 = 代码从哪来
    TestAssert.eq("合并结果 → 箭头 源分支 → dev", "feature/login → dev",
                      PipelineRole.triggerLabel(
                          pipe("1", "success", TGT, MERGE_SHA, "push"), SRC, TGT, MERGE_SHA));
    TestAssert.eq("MR 校验 → 箭头 源分支 → dev", "feature/login → dev",
                      PipelineRole.triggerLabel(
                          pipe("3", "success", SRC, "abc", "merge_request_event"), SRC, TGT, ""));
    TestAssert.eq("源分支 push → 仅分支名", "feature/login",
                      PipelineRole.triggerLabel(
                          pipe("4", "success", SRC, "abc", "push"), SRC, TGT, ""));
    TestAssert.eq("目标分支他人 push → 仅分支名", "dev",
                      PipelineRole.triggerLabel(
                          pipe("8", "success", TGT, "abc", "push"), SRC, TGT, ""));
    TestAssert.eq("合并结果缺源分支 → 退回 ref", "dev",
                      PipelineRole.triggerLabel(
                          pipe("9", "success", TGT, MERGE_SHA, "push"), "", TGT, MERGE_SHA));
    // 箭头方向全篇统一向右：不得出现左箭头
    TestAssert.eq("合并结果无左箭头", false,
                      PipelineRole.triggerLabel(
                          pipe("1", "success", TGT, MERGE_SHA, "push"), SRC, TGT, MERGE_SHA).contains("←"));
    TestAssert.eq("MR 校验无左箭头", false,
                      PipelineRole.triggerLabel(
                          pipe("3", "success", SRC, "abc", "merge_request_event"), SRC, TGT, "").contains("←"));
    TestAssert.eq("空流水线 → 空串", "", PipelineRole.triggerLabel(null, SRC, TGT, MERGE_SHA));

    // 「来源」列：短标签
    TestAssert.eq("来源 合并", "合并",
                      PipelineRole.shortLabel(
                          pipe("1", "success", TGT, MERGE_SHA, "push"), SRC, TGT, MERGE_SHA));
    TestAssert.eq("来源 MR 校验", "MR 校验",
                      PipelineRole.shortLabel(
                          pipe("3", "success", SRC, "abc", "merge_request_event"), SRC, TGT, ""));
    TestAssert.eq("来源 推送", "推送",
                      PipelineRole.shortLabel(
                          pipe("4", "success", SRC, "abc", "push"), SRC, TGT, ""));
    TestAssert.eq("来源 目标分支", "目标分支",
                      PipelineRole.shortLabel(
                          pipe("8", "success", TGT, "abc", "push"), SRC, TGT, ""));
    TestAssert.eq("来源 无角色退回 source", "网页",
                      PipelineRole.shortLabel(pipe("6", "success", "other", "x", "web"), SRC, TGT, ""));
  }

  private static PipelineInfo pipe(String id, String status, String ref, String sha, String source) {
    return new PipelineInfo(id, status, ref, sha, "", source, "");
  }
}
