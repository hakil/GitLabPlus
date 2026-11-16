package com.caidailian.gitlabplus.git;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import git4idea.fetch.GitFetchResult;
import git4idea.fetch.GitFetchSupport;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;

import java.util.List;

/**
 * 合并后的本地 Git 刷新：{@code git fetch --all --prune}。
 *
 * <p>合并发生在远端，本地引用与分支视图不会自动更新；fetch + prune 之后 IDEA 的
 * VCS 面板、分支树与 incoming/outgoing 计数由平台自身刷新。</p>
 */
public final class GitRefresher {

  private static final Logger LOG = Logger.getInstance(GitRefresher.class);

  private GitRefresher() {
  }

  public record Result(boolean success, int repositoryCount, String message) {
  }

  public static Result fetchAll(Project project) {
    List<GitRepository> repositories = GitRepositoryManager.getInstance(project).getRepositories();
    if (repositories.isEmpty()) {
      return new Result(false, 0, "当前项目没有 Git 仓库");
    }
    try {
      GitFetchResult result = GitFetchSupport.fetchSupport(project).fetchAllRemotes(repositories);
      if (result.isSuccessful()) {
        return new Result(true, repositories.size(),
                           "已刷新 " + repositories.size() + " 个仓库的远端引用");
      }
      return new Result(false, repositories.size(), "git fetch 失败");
    }
    catch (Throwable t) {
      LOG.warn("git fetch failed after merge", t);
      return new Result(false, repositories.size(), "git fetch 异常：" + t.getMessage());
    }
  }
}
