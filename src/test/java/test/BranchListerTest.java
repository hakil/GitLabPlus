package test;

import org.junit.jupiter.api.Test;
import com.caidailian.gitlabplus.git.BranchLister;

import org.junit.jupiter.api.Test;
import java.io.File;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import java.util.List;

/**
 * 分支枚举自测：在临时 git 仓库里真实创建分支，验证列举结果。
 */
class BranchListerTest extends SuiteTestSupport {

    @Test
  void suite() throws Exception {
    Path temp = Files.createTempDirectory("gitlabplus-branches");
    try {
      run(temp.toFile(), "init", "-b", "main");
      run(temp.toFile(), "config", "user.email", "test@example.com");
      run(temp.toFile(), "config", "user.name", "test");
      Files.writeString(temp.resolve("a.txt"), "a\n");
      run(temp.toFile(), "add", "a.txt");
      run(temp.toFile(), "commit", "-m", "init");
      run(temp.toFile(), "branch", "feature/login");
      run(temp.toFile(), "checkout", "-b", "feature/pay");

      BranchLister.Branches branches = BranchLister.list(temp.toFile());
      TestAssert.eq("本地分支非空", true, !branches.local().isEmpty());
      TestAssert.eq("含 main", true, branches.local().contains("main"));
      TestAssert.eq("含 feature/login", true, branches.local().contains("feature/login"));
      TestAssert.eq("含当前分支 feature/pay", true, branches.local().contains("feature/pay"));

      List<String> candidates = branches.candidates();
      TestAssert.eq("候选项包含本地", true, candidates.contains("main"));
      TestAssert.eq("候选项无重复", candidates.size(),
                        (int) candidates.stream().distinct().count());

      File notRepo = Files.createTempDirectory("gitlabplus-notrepo").toFile();
      BranchLister.Branches empty = BranchLister.list(notRepo);
      TestAssert.eq("非仓库返回空", true, empty.isEmpty());
      deleteTree(notRepo.toPath());
    }
    finally {
      deleteTree(temp);
    }
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
          .sorted((a, b) -> b.getNameCount() - a.getNameCount())
          .forEach(p -> p.toFile().delete());
    }
    catch (Exception ignored) {
      // 清理失败不影响断言
    }
  }
}
