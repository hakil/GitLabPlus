package com.caidailian.gitlabplus.git;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 调用 git 命令的统一入口。
 *
 * <p>为什么需要它：直接用 {@code ProcessBuilder + waitFor()} 有三处隐患，</p>
 * <ol>
 *   <li><b>没有超时</b>：git 等待凭据输入或网络挂住时，调用线程会永久阻塞，
 *       调用方（通常是池化线程里的 UI 动作）就再也回不来；</li>
 *   <li><b>管道死锁</b>：只读 stdout 不读 stderr，输出量大时子进程会卡在写 stderr；</li>
 *   <li><b>stderr 混入</b>：合并两个流后，git 的错误文本会被当成命令结果解析。</li>
 * </ol>
 * <p>这里用两个守护线程分别排空输出流，主线程带超时等待，超时则强制销毁子进程。</p>
 */
public final class GitProcess {

  public static final int DEFAULT_TIMEOUT_SECONDS = 60;
  /** 推送/拉取这类网络操作给更长的窗口。 */
  public static final int NETWORK_TIMEOUT_SECONDS = 180;

  private GitProcess() {
  }

  public record Result(int exitCode, String stdout, String stderr, boolean timedOut) {

    public boolean ok() {
      return !timedOut && exitCode == 0;
    }

    public String combined() {
      return (stdout + "\n" + stderr).trim();
    }
  }

  public static Result run(File workDir, String... args) {
    return run(workDir, DEFAULT_TIMEOUT_SECONDS, args);
  }

  public static Result run(File workDir, int timeoutSeconds, String... args) {
    List<String> command = new ArrayList<>();
    command.add("git");
    command.addAll(List.of(args));
    Process process = null;
    try {
      process = new ProcessBuilder(command)
          .directory(workDir)
          .start();
      StringBuilder stdout = new StringBuilder();
      StringBuilder stderr = new StringBuilder();
      Thread outReader = drain(process.getInputStream(), stdout);
      Thread errReader = drain(process.getErrorStream(), stderr);

      boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        joinQuietly(outReader, 2000);
        joinQuietly(errReader, 2000);
        return new Result(-1, stdout.toString(), stderr.toString(), true);
      }
      joinQuietly(outReader, 5000);
      joinQuietly(errReader, 5000);
      return new Result(process.exitValue(), stdout.toString(), stderr.toString(), false);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      destroyQuietly(process);
      return new Result(-1, "", "interrupted", false);
    }
    catch (Exception e) {
      destroyQuietly(process);
      return new Result(-1, "", String.valueOf(e.getMessage()), false);
    }
  }

  private static Thread drain(InputStream stream, StringBuilder sink) {
    Thread thread = new Thread(() -> {
      try (InputStream in = stream) {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
          synchronized (sink) {
            sink.append(new String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8));
          }
        }
      }
      catch (Exception ignored) {
        // 进程被销毁时流会关闭，属预期
      }
    }, "gitlabplus-git-drain");
    thread.setDaemon(true);
    thread.start();
    return thread;
  }

  private static void joinQuietly(Thread thread, long millis) {
    if (thread == null) {
      return;
    }
    try {
      thread.join(millis);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static void destroyQuietly(Process process) {
    if (process == null) {
      return;
    }
    try {
      process.destroyForcibly();
    }
    catch (Exception ignored) {
      // 已退出
    }
  }
}
