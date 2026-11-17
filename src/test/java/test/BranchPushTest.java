package test;

import org.junit.jupiter.api.Test;
import com.hakil.gitlabplus.git.BranchLister;
import org.junit.jupiter.api.Test;
import com.hakil.gitlabplus.git.BranchPusher;

import org.junit.jupiter.api.Test;
import java.io.File;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import java.util.List;

/**
 * 分支来源判定 + 推送（含被拒绝场景）自测。
 *
 * <p>用真实的 bare 远程仓库构造场景，而不是 mock：
 * 一个非 fast-forward 的推送必须被判为 rejected，且建议文案要指向 Pull。</p>
 */
class BranchPushTest extends SuiteTestSupport {

    @Test
  void suite() throws Exception {
    // ---- 纯逻辑：本地/远程判定 ----
    BranchLister.Branches b = new BranchLister.Branches(
        List.of("main", "feature/login", "feature/unpushed"),
        List.of("main", "dev", "feature/login"));
    TestAssert.eq("isLocal(main)", true, b.isLocal("main"));
    TestAssert.eq("isRemote(dev)", true, b.isRemote("dev"));
    TestAssert.eq("isPushed(main)", true, b.isPushed("main"));
    TestAssert.eq("needsPush(未推送本地)", true, b.needsPush("feature/unpushed"));
    TestAssert.eq("needsPush(已推送) = false", false, b.needsPush("feature/login"));
    TestAssert.eq("needsPush(仅远程) = false", false, b.needsPush("dev"));
    TestAssert.eq("isRemoteOnly(dev)", true, b.isRemoteOnly("dev"));
    TestAssert.eq("isRemoteOnly(feature/login) = false", false, b.isRemoteOnly("feature/login"));
    TestAssert.eq("sourceCandidates 去重", 4, b.sourceCandidates().size());
    TestAssert.eq("targetCandidates 远程优先", "main", b.targetCandidates().get(0));

    // ---- 真实 git：推送成功与被拒绝 ----
    Path temp = Files.createTempDirectory("gitlabplus-push");
    try {
      File remote = temp.resolve("remote.git").toFile();
      run(temp.toFile(), "init", "--bare", remote.getPath());

      File localA = temp.resolve("a").toFile();
      run(temp.toFile(), "clone", remote.getPath(), localA.getPath());
      configure(localA);
      Files.writeString(localA.toPath().resolve("a.txt"), "a\n");
      run(localA, "add", ".");
      run(localA, "commit", "-m", "init");
      run(localA, "push", "origin", "HEAD:main");

      // A 推送一个新分支应成功
      run(localA, "checkout", "-b", "feature/new");
      Files.writeString(localA.toPath().resolve("b.txt"), "b\n");
      run(localA, "add", ".");
      run(localA, "commit", "-m", "new feature");
      BranchPusher.Result okPush = BranchPusher.push(localA, "feature/new");
      TestAssert.eq("新分支推送成功", true, okPush.pushed());
      TestAssert.eq("成功时 advice 为空", "", BranchPusher.advice(okPush, "feature/new"));

      // 核心回归：推送后 ahead=0；本地又添提交后 ahead>0（否则会创建出空变更的 MR）
      TestAssert.eq("push 后 ahead=0", 0, BranchPusher.aheadCount(localA, "feature/new"));
      Files.writeString(localA.toPath().resolve("c.txt"), "c\n");
      run(localA, "add", ".");
      run(localA, "commit", "-m", "more work");
      TestAssert.eq("本地新提交后 ahead>0", true,
                        BranchPusher.aheadCount(localA, "feature/new") > 0);
      TestAssert.eq("未推送过的分支 ahead=-1", -1,
                        BranchPusher.aheadCount(localA, "feature/never-pushed"));
      TestAssert.eq("空分支名 ahead=-1", -1, BranchPusher.aheadCount(localA, "  "));
      TestAssert.eq("workDir 为 null 时 ahead=-1", -1, BranchPusher.aheadCount(null, "x"));

      // B 往同名分支写不同历史，制造 non-fast-forward
      File localB = temp.resolve("b").toFile();
      run(temp.toFile(), "clone", remote.getPath(), localB.getPath());
      configure(localB);
      run(localB, "checkout", "-b", "feature/conflict");
      Files.writeString(localB.toPath().resolve("x.txt"), "x\n");
      run(localB, "add", ".");
      run(localB, "commit", "-m", "b side");
      run(localB, "push", "origin", "feature/conflict");

      // A 独立开同名分支（不同历史）
      run(localA, "checkout", "-b", "feature/conflict", "HEAD~1");
      Files.writeString(localA.toPath().resolve("y.txt"), "y\n");
      run(localA, "add", ".");
      run(localA, "commit", "-m", "a side");
      BranchPusher.Result rejectedPush = BranchPusher.push(localA, "feature/conflict");
      TestAssert.eq("非快进推送被拒", false, rejectedPush.pushed());
      TestAssert.eq("识别为 rejected", true, rejectedPush.rejected());
      TestAssert.eq("建议文案指向 Pull", true,
                        BranchPusher.advice(rejectedPush, "feature/conflict").contains("Pull"));

      // 空分支名 -> 直接失败，不执行命令
      BranchPusher.Result empty = BranchPusher.push(localA, "  ");
      TestAssert.eq("空分支名失败", false, empty.pushed());
      TestAssert.eq("空分支名不算 rejected", false, empty.rejected());
    }
    finally {
      deleteTree(temp);
    }
  }

  private static void configure(File repo) throws Exception {
    run(repo, "config", "user.email", "test@example.com");
    run(repo, "config", "user.name", "test");
  }

  private static void run(File dir, String... args) throws Exception {
    List<String> cmd = new java.util.ArrayList<>();
    cmd.add("git");
    cmd.addAll(List.of(args));
    Process process = new ProcessBuilder(cmd)
        .directory(dir)
        .redirectErrorStream(true)
        .start();
    process.getInputStream().readAllBytes();
    process.waitFor();
  }

  private static void deleteTree(Path path) {
    try {
      Files.walk(path)
          .sorted((a, c) -> c.getNameCount() - a.getNameCount())
          .forEach(p -> p.toFile().delete());
    }
    catch (Exception ignored) {
      // 清理失败不影响断言
    }
  }
}
