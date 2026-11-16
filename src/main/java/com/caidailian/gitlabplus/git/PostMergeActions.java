package com.caidailian.gitlabplus.git;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 合并成功后的本地 Git 收尾动作（全部可选，默认关闭）。
 *
 * <p>为什么用命令行而不是 {@code GitBrancher}：IDEA 262 拆分 vcs-git 后
 * {@code GitLocalBranch} 的父类、{@code GitReference} 等不在插件可编译的
 * classpath 上（已实测缺失），而 {@code git} 本身在 PATH 中可用。
 * 所以这里用 {@link GeneralCommandLine} 直接调 git，行为可预期且不依赖内部类。</p>
 */
public final class PostMergeActions {

  private static final Logger LOG = Logger.getInstance(PostMergeActions.class);
  private static final int TIMEOUT_MS = 60_000;

  private PostMergeActions() {
  }

  /** 单步执行结果。 */
  public record Step(String name, boolean ok, String detail) {
    @Override
    public String toString() {
      return (ok ? "✓ " : "✗ ") + name + (ok || detail == null || detail.isBlank() ? "" : " — " + detail);
    }
  }

  /** 全部步骤。 */
  public record Result(List<Step> steps) {
    public boolean isEmpty() {
      return steps.isEmpty();
    }

    /** 用于通知文案的多行摘要；无动作时返回空串。 */
    public String summary() {
      if (steps.isEmpty()) {
        return "";
      }
      StringBuilder sb = new StringBuilder();
      for (Step step : steps) {
        sb.append("<br>").append(step);
      }
      return sb.toString();
    }
  }

  /**
   * 依次执行：切到目标分支 → 删除本地源分支（均受开关控制）。
   *
   * <p>安全性：不删当前所在分支；分支名与目标分支相同时跳过；单步失败不影响其他步骤。</p>
   */
  public static Result apply(Project project,
                             String targetBranch,
                             String sourceBranch,
                             boolean checkoutTarget,
                             boolean deleteLocalSource) {
    if (!checkoutTarget && !deleteLocalSource) {
      return new Result(List.of());
    }
    List<Step> steps = new ArrayList<>();
    for (GitRepository repository : GitRepositoryManager.getInstance(project).getRepositories()) {
      VirtualFile root = repository.getRoot();
      if (root == null) {
        continue;
      }
      File dir = new File(root.getPath());
      String current = currentBranch(dir);

      if (checkoutTarget && notBlank(targetBranch) && !targetBranch.equals(current)) {
        steps.add(runGit(dir, "切到 " + targetBranch, "checkout", targetBranch));
      }
      if (deleteLocalSource && notBlank(sourceBranch)) {
        String nowCurrent = currentBranch(dir);
        if (sourceBranch.equals(nowCurrent)) {
          steps.add(new Step("删除本地分支 " + sourceBranch, false, "该分支是当前分支，已跳过"));
        }
        else if (sourceBranch.equals(targetBranch)) {
          steps.add(new Step("删除本地分支 " + sourceBranch, false, "源分支与目标分支相同，已跳过"));
        }
        else {
          steps.add(runGit(dir, "删除本地分支 " + sourceBranch, "branch", "-D", sourceBranch));
        }
      }
    }
    return new Result(steps);
  }

  private static Step runGit(File workDir, String label, String... args) {
    try {
      List<String> command = new ArrayList<>();
      command.add("git");
      command.addAll(List.of(args));
      GeneralCommandLine cmd = new GeneralCommandLine(command)
          .withWorkDirectory(workDir)
          .withCharset(StandardCharsets.UTF_8);
      ProcessOutput output = new CapturingProcessHandler(cmd).runProcess(TIMEOUT_MS);
      if (output.getExitCode() == 0) {
        return new Step(label, true, "");
      }
      String detail = (output.getStderr() + " " + output.getStdout()).trim();
      return new Step(label, false, detail.length() > 300 ? detail.substring(0, 300) + "…" : detail);
    }
    catch (Throwable t) {
      LOG.warn("post-merge git action failed: " + String.join(" ", args), t);
      return new Step(label, false, t.getMessage());
    }
  }

  /** 读 {@code .git/HEAD} 得当前分支（detached 时返回 null）。 */
  public static String currentBranch(File workDir) {
    try {
      File head = new File(workDir, ".git/HEAD");
      if (!head.isFile()) {
        return null;
      }
      String text = Files.readString(head.toPath(), StandardCharsets.UTF_8).trim();
      if (text.startsWith("ref: refs/heads/")) {
        return text.substring("ref: refs/heads/".length()).trim();
      }
    }
    catch (Throwable t) {
      LOG.info("cannot read HEAD in " + workDir, t);
    }
    return null;
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }
}
