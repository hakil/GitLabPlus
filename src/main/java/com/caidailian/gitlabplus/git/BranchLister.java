package com.caidailian.gitlabplus.git;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 分支枚举（供「新建 MR」下拉使用）。
 *
 * <p>用 {@code git for-each-ref} 而不是 git4idea 的分支类：IDEA 262 拆分
 * vcs-git 后 {@code GitLocalBranch} 的父类等类不在插件可编译 classpath 上。</p>
 */
public final class BranchLister {

  private static final int TIMEOUT_MS = 20_000;

  private BranchLister() {
  }

  /** 本地分支与远程分支名（远程去掉 {@code origin/} 前缀、剔除 HEAD）。 */
  public record Branches(List<String> local, List<String> remote) {

    /** 下拉候选项：本地优先，再补远程独有名。 */
    public List<String> candidates() {
      LinkedHashSet<String> set = new LinkedHashSet<>(local);
      set.addAll(remote);
      return new ArrayList<>(set);
    }

    public boolean isEmpty() {
      return local.isEmpty() && remote.isEmpty();
    }

    public boolean isLocal(String branch) {
      return branch != null && local.contains(branch);
    }

    public boolean isRemote(String branch) {
      return branch != null && remote.contains(branch);
    }

    /** 本地存在且远程同名存在 -> 已推送。 */
    public boolean isPushed(String branch) {
      return isLocal(branch) && isRemote(branch);
    }

    /** 本地分支但远程没有同名 -> 创建 MR 前需要推送。 */
    public boolean needsPush(String branch) {
      return isLocal(branch) && !isRemote(branch);
    }

    /** 仅远程存在的分支（可作为源或目标，无需先推送）。 */
    public boolean isRemoteOnly(String branch) {
      return !isLocal(branch) && isRemote(branch);
    }

    /** 作为 MR 源分支的候选：本地分支 + 仅远程分支。 */
    public List<String> sourceCandidates() {
      LinkedHashSet<String> set = new LinkedHashSet<>(local);
      for (String branch : remote) {
        if (!local.contains(branch)) {
          set.add(branch);
        }
      }
      return new ArrayList<>(set);
    }

    /** 作为 MR 目标分支的候选：远程分支优先，再补仅本地的。 */
    public List<String> targetCandidates() {
      LinkedHashSet<String> set = new LinkedHashSet<>(remote);
      for (String branch : local) {
        set.add(branch);
      }
      return new ArrayList<>(set);
    }
  }

  /**
   * 取<b>与当前 GitLab 项目对应</b>的仓库的分支。
   *
   * <p>匹配规则：仓库 remote 解析出的项目路径与 {@code gitLabProjectPath} 相等；
   * 没有匹配时退回第一个有分支的仓库（单仓库工程即正常情况）。</p>
   */
  public static Branches forProject(Project project, String gitLabProjectPath) {
    List<GitRepository> repositories = GitRepositoryManager.getInstance(project).getRepositories();
    if (gitLabProjectPath != null && !gitLabProjectPath.isBlank()) {
      for (GitRepository repository : repositories) {
        if (matchesProject(repository, gitLabProjectPath)) {
          Branches branches = branchesOf(repository);
          if (!branches.isEmpty()) {
            return branches;
          }
        }
      }
    }
    return forProject(project);
  }

  private static boolean matchesProject(GitRepository repository, String gitLabProjectPath) {
    for (String url : GitProjectResolver.readRemoteUrls(repository)) {
      GitProjectResolver.Resolved resolved = GitProjectResolver.parseRemoteUrl(url);
      if (resolved != null && resolved.projectPath().equalsIgnoreCase(gitLabProjectPath.trim())) {
        return true;
      }
    }
    return false;
  }

  /** 与当前 GitLab 项目匹配的仓库的当前分支（无匹配则取第一个仓库）。 */
  public static String currentBranch(Project project, String gitLabProjectPath) {
    List<GitRepository> repositories = GitRepositoryManager.getInstance(project).getRepositories();
    if (gitLabProjectPath != null && !gitLabProjectPath.isBlank()) {
      for (GitRepository repository : repositories) {
        if (matchesProject(repository, gitLabProjectPath)) {
          VirtualFile root = repository.getRoot();
          if (root != null) {
            return PostMergeActions.currentBranch(new File(root.getPath()));
          }
        }
      }
    }
    for (GitRepository repository : repositories) {
      VirtualFile root = repository.getRoot();
      if (root != null) {
        String branch = PostMergeActions.currentBranch(new File(root.getPath()));
        if (branch != null && !branch.isBlank()) {
          return branch;
        }
      }
    }
    return null;
  }

  /**
   * 取与指定 GitLab 项目匹配的仓库工作目录（用于执行 git 命令）。
   *
   * <p>必须与分支列表来源一致：推错仓库会推送一个不含变更的分支，
   * 导致创建出的 MR 没有 diff。</p>
   */
  public static File workDirFor(Project project, String gitLabProjectPath) {
    List<GitRepository> repositories = GitRepositoryManager.getInstance(project).getRepositories();
    if (gitLabProjectPath != null && !gitLabProjectPath.isBlank()) {
      for (GitRepository repository : repositories) {
        if (matchesProject(repository, gitLabProjectPath)) {
          VirtualFile root = repository.getRoot();
          if (root != null) {
            return new File(root.getPath());
          }
        }
      }
    }
    for (GitRepository repository : repositories) {
      VirtualFile root = repository.getRoot();
      if (root != null) {
        return new File(root.getPath());
      }
    }
    return null;
  }

  private static Branches branchesOf(GitRepository repository) {
    VirtualFile root = repository.getRoot();
    return root == null ? new Branches(List.of(), List.of()) : list(new File(root.getPath()));
  }

  /** 取项目内第一个有分支的仓库。 */
  public static Branches forProject(Project project) {
    for (GitRepository repository : GitRepositoryManager.getInstance(project).getRepositories()) {
      VirtualFile root = repository.getRoot();
      if (root == null) {
        continue;
      }
      Branches branches = list(new File(root.getPath()));
      if (!branches.isEmpty()) {
        return branches;
      }
    }
    return new Branches(List.of(), List.of());
  }

  public static Branches list(File workDir) {
    List<String> local = forEachRef(workDir, "refs/heads");
    List<String> remote = new ArrayList<>();
    for (String ref : forEachRef(workDir, "refs/remotes")) {
      int slash = ref.indexOf('/');
      if (slash <= 0 || slash == ref.length() - 1) {
        continue;
      }
      String name = ref.substring(slash + 1);
      if ("HEAD".equals(name)) {
        continue;
      }
      remote.add(name);
    }
    return new Branches(local, remote);
  }

  /** 用 git 命令枚举引用（超时与管道排空由 {@link GitProcess} 处理）。 */
  private static List<String> forEachRef(File workDir, String refPrefix) {
    List<String> result = new ArrayList<>();
    GitProcess.Result run = GitProcess.run(workDir, 30, "for-each-ref", "--format=%(refname:short)", refPrefix);
    // 不合并 stderr：否则 git 的错误文本会被当成分支名（非仓库目录下的真实缺陷）
    if (!run.ok()) {
      return List.of();
    }
    for (String line : run.stdout().split("\n")) {
      String name = line.trim();
      if (!name.isEmpty()) {
        result.add(name);
      }
    }
    return result;
  }
}
