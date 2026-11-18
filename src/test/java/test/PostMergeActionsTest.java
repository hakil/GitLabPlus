package test;

import org.junit.jupiter.api.Test;
import com.hakil.gitlabplus.git.PostMergeActions;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 合并后收尾动作的纯逻辑自测：HEAD 解析、结果摘要、步骤文案。
 */
class PostMergeActionsTest extends SuiteTestSupport {

    @Test
  void suite() throws Exception {
    Path temp = Files.createTempDirectory("gitlabplus-head");
    try {
      Path gitDir = temp.resolve(".git");
      Files.createDirectories(gitDir);

      Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/feature/login\n", StandardCharsets.UTF_8);
      TestAssert.eq("分支名解析", "feature/login", PostMergeActions.currentBranch(temp.toFile()));

      Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/dev\n", StandardCharsets.UTF_8);
      TestAssert.eq("简单分支名", "dev", PostMergeActions.currentBranch(temp.toFile()));

      Files.writeString(gitDir.resolve("HEAD"), "abc123def456\n", StandardCharsets.UTF_8);
      TestAssert.eq("detached HEAD -> null", null, PostMergeActions.currentBranch(temp.toFile()));

      Files.writeString(gitDir.resolve("HEAD"), "  ref: refs/heads/trimmed  \n", StandardCharsets.UTF_8);
      TestAssert.eq("首尾空白被裁掉", "trimmed", PostMergeActions.currentBranch(temp.toFile()));
    }
    finally {
      deleteTree(temp);
    }

    File noGit = Files.createTempDirectory("gitlabplus-nogit").toFile();
    TestAssert.eq("无 .git -> null", null, PostMergeActions.currentBranch(noGit));
    deleteTree(noGit.toPath());

    TestAssert.eq("空结果摘要为空串", "", new PostMergeActions.Result(List.of()).summary());
    TestAssert.eq("空结果 isEmpty", true, new PostMergeActions.Result(List.of()).isEmpty());

    PostMergeActions.Result result = new PostMergeActions.Result(List.of(
        new PostMergeActions.Step("切到 dev", true, ""),
        new PostMergeActions.Step("删除本地分支 feature/x", false, "error: branch not found")));
    TestAssert.eq("多步骤 isEmpty=false", false, result.isEmpty());
    String summary = result.summary();
    TestAssert.eq("摘要含成功项", true, summary.contains("✓ 切到 dev"));
    TestAssert.eq("摘要含失败原因", true, summary.contains("✗ 删除本地分支 feature/x — error: branch not found"));
    TestAssert.eq("摘要以换行开头", true, summary.startsWith("<br>"));
  }

  private static void deleteTree(Path path) {
    try {
      Files.walk(path)
          .sorted((a, b) -> b.getNameCount() - a.getNameCount())
          .forEach(p -> p.toFile().delete());
    }
    catch (Exception ignored) {
      // 临时目录清理失败不影响断言
    }
  }
}
