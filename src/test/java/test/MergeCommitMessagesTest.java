package test;

import com.hakil.gitlabplus.model.MergeCommitMessages;
import org.junit.jupiter.api.Test;

/**
 * 合并提交信息取值规则。
 *
 * <p>回归两个现场问题：合并对话框的 squash 被无条件勾选；
 * squash 时合并信息与压缩信息同时发送导致同一标题出现两次。</p>
 */
class MergeCommitMessagesTest extends SuiteTestSupport {

  @Test
  void messagesAreMutuallyExclusive() {
    TestAssert.eq("squash 时不发合并信息", null, MergeCommitMessages.merge(true, "标题"));
    TestAssert.eq("非 squash 时发合并信息", "标题", MergeCommitMessages.merge(false, "标题"));
    TestAssert.eq("合并信息为空白视为不发", null, MergeCommitMessages.merge(false, "   "));
    TestAssert.eq("合并信息为 null 视为不发", null, MergeCommitMessages.merge(false, null));

    TestAssert.eq("squash 时发压缩信息", "标题", MergeCommitMessages.squash(true, "标题"));
    TestAssert.eq("非 squash 时不发压缩信息", null, MergeCommitMessages.squash(false, "标题"));
    TestAssert.eq("压缩信息为空白视为不发", null, MergeCommitMessages.squash(true, ""));

    TestAssert.eq("两者不会同时非空",
                  true,
                  !(MergeCommitMessages.merge(true, "a") != null
                    && MergeCommitMessages.squash(true, "a") != null));
  }

  @Test
  void initialMessageIsEmptyToAvoidDuplicateTitle() {
    // GitLab 会把自定义提交信息追加到自动生成的标题之后：预填 commitMessage 就会重复
    TestAssert.eq("默认提交信息为空", "", MergeCommitMessages.initialMessage());
  }

  @Test
  void defaultMessageShownInHint() {
    TestAssert.eq("squash 展示压缩默认值", "sq",
                  MergeCommitMessages.defaultMessage(true, "sq", "merge", "title"));
    TestAssert.eq("非 squash 展示合并默认值", "merge",
                  MergeCommitMessages.defaultMessage(false, "sq", "merge", "title"));
    TestAssert.eq("默认值为空退化到标题", "title",
                  MergeCommitMessages.defaultMessage(false, "", "  ", "title"));
    TestAssert.eq("全空返回空串", "",
                  MergeCommitMessages.defaultMessage(false, null, null, null));
  }

  @Test
  void squashSelectionRules() {
    TestAssert.eq("强制 squash 时勾选", true, MergeCommitMessages.defaultSquash(true, false));
    TestAssert.eq("MR 已设 squash 时勾选", true, MergeCommitMessages.defaultSquash(false, true));
    TestAssert.eq("都没设时不勾选", false, MergeCommitMessages.defaultSquash(false, false));

    TestAssert.eq("强制 squash 时不可取消", false, MergeCommitMessages.squashEditable(true, false));
    TestAssert.eq("squashReadOnly 时不可改", false, MergeCommitMessages.squashEditable(false, true));
    TestAssert.eq("普通合并可勾选", true, MergeCommitMessages.squashEditable(false, false));
  }
}
