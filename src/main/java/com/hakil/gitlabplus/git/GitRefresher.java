package com.hakil.gitlabplus.git;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.fetch.GitFetchResult;
import git4idea.fetch.GitFetchSupport;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 合并后的本地 Git 刷新。
 *
 * <p>合并发生在远端，本地引用与分支视图不会自动更新；fetch + prune 之后 IDEA 的
 * VCS 面板、分支树与 incoming/outgoing 计数由平台自身刷新。</p>
 *
 * <p>多仓库工程里一次性 fetch 全部仓库会让合并结果等待所有网络往返。因此只对
 * <b>本次合并对应的仓库</b>同步 fetch（它的状态直接影响刚完成的操作），
 * 其余仓库转到后台线程异步 fetch。</p>
 */
public final class GitRefresher {

  private static final Logger LOG = Logger.getInstance(GitRefresher.class);

  private GitRefresher() {
  }

  public record Result(boolean success, int repositoryCount, String message) {
  }

  /**
   * 同步刷新与本次操作对应的仓库，其余仓库后台异步刷新。
   *
   * @param primaryWorkDir 目标仓库工作目录；为 {@code null} 时退化为「同步刷新全部」
   *                       之外的路径（不阻塞，全部转后台）
   */
  public static Result fetchPrimaryThenOthersAsync(Project project, File primaryWorkDir) {
    List<GitRepository> repositories = GitRepositoryManager.getInstance(project).getRepositories();
    if (repositories.isEmpty()) {
      return new Result(false, 0, "当前项目没有 Git 仓库");
    }
    List<String> roots = new ArrayList<>();
    for (GitRepository repository : repositories) {
      VirtualFile root = repository.getRoot();
      roots.add(root == null ? null : root.getPath());
    }
    int primaryIndex = indexOfRoot(roots, primaryWorkDir);

    List<GitRepository> others = new ArrayList<>(repositories);
    GitRepository primary = null;
    if (primaryIndex >= 0) {
      primary = others.remove(primaryIndex);
    }

    Result result;
    if (primary != null) {
      result = fetchSync(project, List.of(primary), repositoryName(primary));
    }
    else {
      // 无法确定主仓库时不在前台等待任何网络操作
      result = new Result(true, 0, "");
    }

    if (!others.isEmpty()) {
      List<GitRepository> background = List.copyOf(others);
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        try {
          GitFetchResult fetched = GitFetchSupport.fetchSupport(project).fetchAllRemotes(background);
          LOG.info("background git fetch: " + background.size() + " repositories, successful="
                       + fetched.isSuccessful());
        }
        catch (Throwable t) {
          // 后台刷新失败不影响合并结果
          LOG.info("background git fetch failed", t);
        }
      });
    }
    return result;
  }

  private static Result fetchSync(Project project, List<GitRepository> repositories, String name) {
    try {
      GitFetchResult result = GitFetchSupport.fetchSupport(project).fetchDefaultRemote(repositories);
      if (result.isSuccessful()) {
        return new Result(true, repositories.size(), "已刷新 " + name + " 的远端引用");
      }
      return new Result(false, repositories.size(), "git fetch 失败：" + name);
    }
    catch (Throwable t) {
      LOG.warn("git fetch failed after merge", t);
      return new Result(false, repositories.size(), "git fetch 异常：" + t.getMessage());
    }
  }

  private static String repositoryName(GitRepository repository) {
    VirtualFile root = repository.getRoot();
    return root == null ? "仓库" : root.getName();
  }

  /**
   * 在仓库根路径列表里定位工作目录。
   *
   * @return 匹配项下标，找不到（或参数为空）返回 {@code -1}
   */
  public static int indexOfRoot(List<String> roots, File workDir) {
    if (roots == null || workDir == null) {
      return -1;
    }
    String target = normalize(workDir.getPath());
    for (int i = 0; i < roots.size(); i++) {
      String root = roots.get(i);
      if (root != null && normalize(root).equals(target)) {
        return i;
      }
    }
    return -1;
  }

  /** 去掉尾部分隔符并统一分隔符，便于比较 Windows 下的 \ 与 / 混用。 */
  public static String normalize(String path) {
    if (path == null) {
      return "";
    }
    String normalized = path.replace('\\', '/');
    while (normalized.length() > 1 && normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }
}
