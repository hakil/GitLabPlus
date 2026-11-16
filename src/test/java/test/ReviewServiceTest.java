package test;

import org.junit.jupiter.api.Test;
import com.caidailian.gitlabplus.model.Discussion;
import org.junit.jupiter.api.Test;
import com.caidailian.gitlabplus.service.GitLabReviewService;
import org.junit.jupiter.api.Test;
import com.caidailian.gitlabplus.service.GitLabService;
import org.junit.jupiter.api.Test;
import com.caidailian.gitlabplus.ui.MergeRequestDetailPanel;
import org.junit.jupiter.api.Test;
import com.caidailian.gitlabplus.ui.ReviewPanel;
import org.junit.jupiter.api.Test;
import com.caidailian.gitlabplus.storage.ViewedNotesStore;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;

/**
 * P2 服务层自测：viewed 标记的 headSha 语义 + 草稿评论解析。
 */
class ReviewServiceTest extends SuiteTestSupport {

    @Test
  void suite() {
    TestAssert.eq("无记录 -> 未读", false, ViewedNotesStore.isValidSha(null, "abc"));
    TestAssert.eq("空记录 -> 未读", false, ViewedNotesStore.isValidSha("  ", "abc"));
    TestAssert.eq("sha 相同 -> 已读", true, ViewedNotesStore.isValidSha("abc", "abc"));
    TestAssert.eq("sha 变化 -> 未读", false, ViewedNotesStore.isValidSha("abc", "def"));
    TestAssert.eq("当前 sha 缺失 -> 已读", true, ViewedNotesStore.isValidSha("abc", null));

    // 关闭/重开：GraphQL 枚举不稳定，改走 REST state_event
    TestAssert.eq("CLOSE -> close", "close", GitLabService.stateEvent("CLOSE"));
    TestAssert.eq("REOPEN -> reopen", "reopen", GitLabService.stateEvent("REOPEN"));
    TestAssert.eq("closed -> close", "close", GitLabService.stateEvent("closed"));
    TestAssert.eq("reopened -> reopen", "reopen", GitLabService.stateEvent("reopened"));
    TestAssert.eq("小写 close 幂等", "close", GitLabService.stateEvent("close"));
    TestAssert.eq("null 默认 close", "close", GitLabService.stateEvent(null));

    // 点赞目标必须跳过 system note（GitLab 拒绝对系统消息加 reaction）
    var notesWithSystem = Discussion.from(JsonParser.parseString("""
        {"id":"d1","notes":[
          {"id":"10069","system":true,"body":"added 1 commit"},
          {"id":"10070","system":false,"body":"这里应该用常量"}
        ]}
        """).getAsJsonObject());
    TestAssert.eq("跳过 system note 取真实评论", "10070",
                      ReviewPanel.awardTargetNoteId(notesWithSystem));

    var onlySystem = Discussion.from(JsonParser.parseString("""
        {"id":"d2","notes":[{"id":"10069","system":true,"body":"added 1 commit"}]}
        """).getAsJsonObject());
    TestAssert.eq("仅系统消息时无目标", null, ReviewPanel.awardTargetNoteId(onlySystem));
    TestAssert.eq("空讨论无目标", null, ReviewPanel.awardTargetNoteId(null));

    // reaction 解析需带 award id 与用户名：toggle 靠它们判断“我是否已点过”
    var noteWithAwards = Discussion.from(JsonParser.parseString("""
        {"id":"d3","notes":[{"id":"1001","system":false,"body":"x","award_emoji":[
          {"id":55,"name":"thumbsup","user":{"username":"lishiqi"}},
          {"id":56,"name":"eyes","user":{"username":"zhangsan"}}
        ]}]}
        """).getAsJsonObject());
    var awards = noteWithAwards.notes.get(0).awards;
    TestAssert.eq("award id 解析", "55", awards.get(0).id());
    TestAssert.eq("award 用户名解析", "lishiqi", awards.get(0).username());
    TestAssert.eq("award 计数摘要", "thumbsup 1 eyes 1", noteWithAwards.notes.get(0).awardSummary());

    // 409 重复创建：从消息里提取已有 MR 号
    TestAssert.eq("409 提取 MR 号", "!217",
                      GitLabService.existingMergeRequestRef(
                          "Another open merge request already exists for this source branch: !217"));
    TestAssert.eq("无 MR 号返回空", "", GitLabService.existingMergeRequestRef("HTTP 409 @ /x"));
    TestAssert.eq("null 消息空串", "", GitLabService.existingMergeRequestRef(null));

    // 409 识别：无论是 restCall 包装过的还是平台原始消息都要能识别
    TestAssert.eq("409 识别（HTTP）", true, GitLabService.looksLikeConflict("HTTP 409 @ /api/v4/x"));
    TestAssert.eq("409 识别（原始消息）", true,
                      GitLabService.looksLikeConflict(
                          "Server returned HTTP response code: 409 for URL: https://x/y"));
    TestAssert.eq("409 识别（already exists）", true,
                      GitLabService.looksLikeConflict(
                          "Another open merge request already exists for this source branch: !217"));
    TestAssert.eq("404 不算冲突", false, GitLabService.looksLikeConflict("HTTP 404 @ /x"));
    TestAssert.eq("null 不算冲突", false, GitLabService.looksLikeConflict(null));

    // Approve / Revoke 互斥：已批准后 canApprove 会变 false，不能拿它判断能否撤销
    TestAssert.eq("未批准可 Approve", true, MergeRequestDetailPanel.approveEnabled(true, false));
    TestAssert.eq("未批准不可 Revoke", false, MergeRequestDetailPanel.revokeEnabled(false));
    TestAssert.eq("已批准不可再 Approve", false, MergeRequestDetailPanel.approveEnabled(false, true));
    TestAssert.eq("已批准可 Revoke", true, MergeRequestDetailPanel.revokeEnabled(true));
    TestAssert.eq("canApprove=false 且已批准仍可 Revoke", true,
                      MergeRequestDetailPanel.revokeEnabled(true));
  }
}
