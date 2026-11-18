package test;

import com.hakil.gitlabplus.git.BranchLister;
import com.hakil.gitlabplus.git.BranchPusher;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 仓库选择与远程名探测。
 *
 * <p>核心回归：工程内有多个仓库且无一匹配项目路径时必须返回 {@code null}，
 * 不能猜第一个——否则会把分支推送到另一个仓库，
 * 报错形如 {@code src refspec <branch> does not match any}。</p>
 */
class BranchPickTest extends SuiteTestSupport {

  @Test
  void pickRules() {
    File a = new File("A");
    File b = new File("B");

    TestAssert.eq("空候选返回 null", null, BranchLister.pick(List.of(), "g/p"));

    TestAssert.eq("单候选无匹配仍使用", a, BranchLister.pick(
        List.of(new BranchLister.Candidate(a, List.of("https://h/other/x.git"))), "g/p"));

    TestAssert.eq("多候选选匹配项", b, BranchLister.pick(List.of(
        new BranchLister.Candidate(a, List.of("https://h/g/other.git")),
        new BranchLister.Candidate(b, List.of("git@h:g/p.git"))), "g/p"));

    TestAssert.eq("多候选无匹配返回 null", null, BranchLister.pick(List.of(
        new BranchLister.Candidate(a, List.of("https://h/g/x.git")),
        new BranchLister.Candidate(b, List.of("https://h/g/y.git"))), "g/p"));

    TestAssert.eq("空项目多候选返回 null", null, BranchLister.pick(List.of(
        new BranchLister.Candidate(a, List.of()),
        new BranchLister.Candidate(b, List.of())), ""));

    TestAssert.eq("空项目单候选使用", a, BranchLister.pick(
        List.of(new BranchLister.Candidate(a, List.of())), null));

    TestAssert.eq("项目路径大小写不敏感", a, BranchLister.pick(List.of(
        new BranchLister.Candidate(a, List.of("https://h/G/P.git")),
        new BranchLister.Candidate(b, List.of("https://h/g/other.git"))), "g/p"));

    TestAssert.eq("remoteUrls 为 null 不抛", null, BranchLister.pick(List.of(
        new BranchLister.Candidate(a, null),
        new BranchLister.Candidate(b, null)), "g/p"));

    TestAssert.eq("单候选 remoteUrls 为 null 仍可用", a, BranchLister.pick(
        List.of(new BranchLister.Candidate(a, null)), "g/p"));

    TestAssert.eq("匹配第二个 remote 地址", a, BranchLister.pick(List.of(
        new BranchLister.Candidate(a, List.of("https://h/g/z.git", "ssh://h/g/p.git")),
        new BranchLister.Candidate(b, List.of("https://h/g/other.git"))), "g/p"));
  }

  @Test
  void remoteNameRules() throws Exception {
    Path temp = Files.createTempDirectory("gitlabplus-remote");
    try {
      File dir = temp.toFile();
      run(dir, "init", "-b", "main");
      run(dir, "config", "user.email", "test@example.com");
      run(dir, "config", "user.name", "test");

      TestAssert.eq("无远程时回退 origin", "origin", BranchPusher.remoteName(dir));

      run(dir, "remote", "add", "upstream", "https://example.com/x.git");
      TestAssert.eq("仅 upstream 时用 upstream", "upstream", BranchPusher.remoteName(dir));

      run(dir, "remote", "add", "origin", "https://example.com/y.git");
      TestAssert.eq("存在 origin 时优先 origin", "origin", BranchPusher.remoteName(dir));

      TestAssert.eq("workDir 为 null 回退 origin", "origin", BranchPusher.remoteName(null));
    }
    finally {
      deleteTree(temp);
    }
  }

  @Test
  void needsPushRules() throws Exception {
    Path temp = Files.createTempDirectory("gitlabplus-needspush");
    try {
      File remote = temp.resolve("remote.git").toFile();
      run(temp.toFile(), "init", "--bare", remote.getPath());

      File local = temp.resolve("a").toFile();
      Files.createDirectories(local.toPath());
      run(local, "init", "-b", "main");
      run(local, "config", "user.email", "test@example.com");
      run(local, "config", "user.name", "test");
      Files.writeString(local.toPath().resolve("a.txt"), "a\n");
      run(local, "add", ".");
      run(local, "commit", "-m", "init");
      run(local, "remote", "add", "origin", remote.getPath());
      run(local, "push", "-u", "origin", "main");

      BranchLister.Branches branches = BranchLister.list(local);
      TestAssert.eq("本地含 main", true, branches.isLocal("main"));

      TestAssert.eq("已同步不推送", false, BranchLister.needsPush(branches, local, "main"));

      Files.writeString(local.toPath().resolve("b.txt"), "b\n");
      run(local, "add", ".");
      run(local, "commit", "-m", "more");
      TestAssert.eq("本地新提交需推送", true, BranchLister.needsPush(branches, local, "main"));

      TestAssert.eq("仅远程分支不推送", false, BranchLister.needsPush(remoteOnlyBranches(), local, "2.0.0"));
      TestAssert.eq("branches 为 null 不推送", false, BranchLister.needsPush(null, local, "main"));
      TestAssert.eq("workDir 为 null 不推送", false, BranchLister.needsPush(branches, null, "main"));
      TestAssert.eq("空分支名不推送", false, BranchLister.needsPush(branches, local, "  "));
    }
    finally {
      deleteTree(temp);
    }
  }

  /** 本地只有 main，远程另有 2.0.0（对齐用户现场：远程版本分支被选为源）。 */
  private static BranchLister.Branches remoteOnlyBranches() {
    return new BranchLister.Branches(List.of("main"), List.of("main", "2.0.0"));
  }

  private static void run(File dir, String... args) throws Exception {
    List<String> cmd = new java.util.ArrayList<>();
    cmd.add("git");
    cmd.addAll(List.of(args));
    Process process = new ProcessBuilder(cmd).directory(dir).redirectErrorStream(true).start();
    process.waitFor();
  }

  private static void deleteTree(Path path) {
    try {
      Files.walk(path)
          .sorted((x, y) -> y.getNameCount() - x.getNameCount())
          .forEach(p -> p.toFile().delete());
    }
    catch (Exception ignored) {
      // 清理失败不影响断言
    }
  }
}
