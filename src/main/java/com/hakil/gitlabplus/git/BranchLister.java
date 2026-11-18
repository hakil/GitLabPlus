package com.hakil.gitlabplus.git;

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
   * <p>仓库选择统一走 {@link #workDirFor}，保证分支列表与后续 git 命令同源。</p>
   */
  public static Branches forProject(Project project, String gitLabProjectPath) {
    File workDir = workDirFor(project, gitLabProjectPath);
    return workDir == null ? new Branches(List.of(), List.of()) : list(workDir);
  }

  /** 与当前 GitLab 项目匹配的仓库的当前分支；无匹配时为 {@code null}。 */
  public static String currentBranch(Project project, String gitLabProjectPath) {
    File workDir = workDirFor(project, gitLabProjectPath);
    return workDir == null ? null : PostMergeActions.currentBranch(workDir);
  }

  /**
   * 仓库候选：工作目录 + 各 remote 地址。
   *
   * <p>独立于 IDEA 的 {@code GitRepository} 以便对选择规则做离线测试。</p>
   */
  public record Candidate(File workDir, List<String> remoteUrls) {
  }

  /**
   * 从候选中选出与 GitLab 项目匹配的工作目录。
   *
   * <p>规则：有匹配取匹配项；无匹配但只有一个候选（单仓库工程）取它；
   * <b>多候选且无匹配返回 {@code null}</b>——不猜测，否则会把分支推送到另一个仓库，
   * 报错形如 {@code src refspec <branch> does not match any}。</p>
   */
  public static File pick(List<Candidate> candidates, String gitLabProjectPath) {
    if (candidates == null || candidates.isEmpty()) {
      return null;
    }
    if (gitLabProjectPath != null && !gitLabProjectPath.isBlank()) {
      String wanted = gitLabProjectPath.trim();
      for (Candidate candidate : candidates) {
        if (candidate.remoteUrls() == null) {
          continue;
        }
        for (String url : candidate.remoteUrls()) {
          GitProjectResolver.Resolved resolved = GitProjectResolver.parseRemoteUrl(url);
          if (resolved != null && resolved.projectPath().equalsIgnoreCase(wanted)) {
            return candidate.workDir();
          }
        }
      }
    }
    return candidates.size() == 1 ? candidates.get(0).workDir() : null;
  }

  /**
   * 取与指定 GitLab 项目匹配的工作目录（用于执行 git 命令）。
   *
   * @return 无可用或无法唯一确定时为 {@code null}，由调用方明确提示，不静默回退
   */
  public static File workDirFor(Project project, String gitLabProjectPath) {
    List<Candidate> candidates = new ArrayList<>();
    for (GitRepository repository : GitRepositoryManager.getInstance(project).getRepositories()) {
      VirtualFile root = repository.getRoot();
      if (root != null) {
        candidates.add(new Candidate(new File(root.getPath()), GitProjectResolver.readRemoteUrls(repository)));
      }
    }
    return pick(candidates, gitLabProjectPath);
  }

  /**
   * 创建 MR 前是否需要推送。
   *
   * <p>仅<b>本地</b>分支才可能推送；仅远程存在的分支直接创建 MR——
   * 本地没有该 ref，对它执行 push 会报 {@code src refspec <branch> does not match any}。</p>
   */
  public static boolean needsPush(Branches branches, File workDir, String branch) {
    if (branches == null || workDir == null || branch == null || branch.isBlank()) {
      return false;
    }
    if (!branches.isLocal(branch)) {
      return false;
    }
    return BranchPusher.aheadCount(workDir, branch) != 0;
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
