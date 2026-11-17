package com.hakil.gitlabplus.git;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 把本地分支推送到 origin（创建 MR 前的必要步骤）。
 *
 * <p>安全原则：<b>绝不强推</b>。若推送被拒绝（远程有本地没有的提交），
 * 直接失败并把 git 的原文返回给调用方，由用户先 {@code git pull} 处理冲突，
 * 而不是自动 {@code --force} 或自动合并。</p>
 */
public final class BranchPusher {

  private BranchPusher() {
  }

  /**
   * @param pushed  是否推送成功（或本来就无需推送）
   * @param rejected 是否被服务端拒绝（non-fast-forward / fetch first）
   * @param detail   失败时 git 的 stderr 摘要
   */
  public record Result(boolean pushed, boolean rejected, String detail) {
  }

  /**
   * 本地分支领先 origin 同名分支的提交数。
   *
   * <p><b>这是判断「是否要推送」的正确依据</b>：仅看远程是否已存在同名分支会漏掉
   * 「远程有旧分支、本地又添了新提交」的情况，那会创建出一个没有任何变更的 MR。</p>
   *
   * @return &gt;0 有未推送提交；0 已同步；-1 远程无该分支（或命令失败）
   */
  public static int aheadCount(File workDir, String branch) {
    if (workDir == null || branch == null || branch.isBlank()) {
      return -1;
    }
    GitProcess.Result run = GitProcess.run(workDir, 30,
                                          "rev-list", "--count", "origin/" + branch + ".." + branch);
    if (!run.ok()) {
      return -1;
    }
    try {
      return Integer.parseInt(run.stdout().trim());
    }
    catch (NumberFormatException e) {
      return -1;
    }
  }

  /** 推送项目内第一个仓库的指定分支到 origin。 */
  public static Result push(Project project, String branch) {
    for (GitRepository repository : GitRepositoryManager.getInstance(project).getRepositories()) {
      VirtualFile root = repository.getRoot();
      if (root == null) {
        continue;
      }
      return push(new File(root.getPath()), branch);
    }
    return new Result(false, false, "项目内没有可用的 Git 仓库");
  }

  public static Result push(File workDir, String branch) {
    if (branch == null || branch.isBlank()) {
      return new Result(false, false, "分支名为空");
    }
    GitProcess.Result run = GitProcess.run(workDir, GitProcess.NETWORK_TIMEOUT_SECONDS,
                                          "push", "-u", "origin", branch);
    if (run.timedOut()) {
      return new Result(false, false,
                        "推送超时（" + GitProcess.NETWORK_TIMEOUT_SECONDS + " 秒），请检查网络或凭据配置");
    }
    String combined = run.combined();
    if (run.ok()) {
      return new Result(true, false, "");
    }
    boolean rejected = combined.contains("[rejected]")
        || combined.contains("non-fast-forward")
        || combined.contains("fetch first")
        || combined.contains("Updates were rejected");
    return new Result(false, rejected, abbreviate(combined));
  }

  /** 给用户的下一步建议（rejected 时说明必须先拉取）。 */
  public static String advice(Result result, String branch) {
    if (result.pushed()) {
      return "";
    }
    if (result.rejected()) {
      return "远程已有本地没有的提交，已停止创建。请先在 IDEA 里对 " + branch
          + " 执行 Pull（必要时先 stash 或解决冲突），成功后再创建 merge request。";
    }
    return "推送失败：" + (result.detail() == null || result.detail().isBlank() ? "未知原因" : result.detail());
  }

  private static String abbreviate(String text) {
    String flat = text.replace('\r', ' ');
    return flat.length() <= 600 ? flat : flat.substring(0, 600) + "…";
  }
}
