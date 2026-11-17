package com.hakil.gitlabplus.service;

import com.hakil.gitlabplus.api.GitLabClient;
import com.hakil.gitlabplus.model.PipelineInfo;
import com.hakil.gitlabplus.model.PipelineState;
import com.hakil.gitlabplus.settings.GitLabPlusSettings;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 流水线「关注」服务：按固定间隔轮询同一条 pipeline，状态变化即时推送到 UI，
 * 结束时弹出 IDEA 通知；到点仍未结束则超时停止。
 *
 * <p><b>为什么不用 IDEA 的 {@code Alarm}</b>：Alarm 与 project 生命周期绑定，
 * 在轮询回调（后台线程）里重排下一次请求时可能被静默丢弃，
 * 表现为「关注后既没有状态推送、也没有结束通知，只能等到超时」。
 * 这里改为显式持有 {@link ScheduledFuture}，每次重排都校验 watch 归属，
 * 行为可预期，且失败会记录日志并提示一次。</p>
 */
@Service(Service.Level.APP)
public final class PipelineWatchService implements Disposable {

  private static final Logger LOG = Logger.getInstance(PipelineWatchService.class);
  private static final String NOTIFICATION_GROUP = "GitLabPlus.Notifications";
  private static final long POLL_MS = 10_000L;
  private static final long FIRST_DELAY_MS = 1_500L;

  /** 单线程守护调度器：同一时刻只轮询一条，避免并发打爆接口。 */
  private static final ScheduledExecutorService SCHEDULER =
      Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "gitlabplus-pipeline-watch");
        thread.setDaemon(true);
        return thread;
      });

  private record Key(String projectPath, String iid) {
  }

  private static final class Watch {
    private final long deadline;
    private final String pipelineId;
    private volatile ScheduledFuture<?> future;
    private int polls;
    private int consecutiveErrors;

    private Watch(long deadline, String pipelineId) {
      this.deadline = deadline;
      this.pipelineId = pipelineId;
    }

    private void cancel() {
      ScheduledFuture<?> current = future;
      if (current != null) {
        current.cancel(false);
      }
    }
  }

  private final Map<Key, Watch> watches = new ConcurrentHashMap<>();

  /** UI 订阅者：状态推送（iid + 最新流水线信息 + 是否已结束）。 */
  public interface Listener {
    void pipelineStateChanged(String iid, PipelineInfo info, boolean finished);
  }

  public static final Topic<Listener> TOPIC =
      Topic.create("GitLabPlus.PipelineWatch", Listener.class);

  public static PipelineWatchService getInstance() {
    return ApplicationManager.getApplication().getService(PipelineWatchService.class);
  }

  public boolean isWatching(String projectPath, String iid) {
    return watches.containsKey(new Key(projectPath, iid));
  }

  /** 剩余关注时间（分钟，向上取整；未关注返回 0）。 */
  public int remainingMinutes(String projectPath, String iid) {
    Watch watch = watches.get(new Key(projectPath, iid));
    if (watch == null) {
      return 0;
    }
    long remaining = watch.deadline - System.currentTimeMillis();
    return remaining <= 0 ? 0 : (int) Math.ceil(remaining / 60_000.0);
  }

  /** 开始关注某条 pipeline（同一 MR 重复关注会替换旧的）。 */
  public void start(Project project, String projectPath, String iid, String pipelineId, GitLabClient client) {
    if (projectPath == null || iid == null || pipelineId == null || pipelineId.isBlank()) {
      LOG.warn("pipeline watch not started: path=" + projectPath + " iid=" + iid + " pipeline=" + pipelineId);
      return;
    }
    Key key = new Key(projectPath, iid);
    cancel(projectPath, iid);
    int timeoutMinutes = Math.max(1, GitLabPlusSettings.getInstance().getPipelineWatchTimeoutMinutes());
    Watch watch = new Watch(System.currentTimeMillis() + timeoutMinutes * 60_000L, pipelineId);
    watches.put(key, watch);
    LOG.info("pipeline watch started: " + projectPath + "!" + iid
        + " pipeline=" + pipelineId + " timeout=" + timeoutMinutes + "min");
    schedule(project, key, watch, client, timeoutMinutes, FIRST_DELAY_MS);
  }

  /** 当前关注的是哪条 pipeline（未关注返回 null）。 */
  public String watchedPipelineId(String projectPath, String iid) {
    Watch watch = watches.get(new Key(projectPath, iid));
    return watch == null ? null : watch.pipelineId;
  }

  public void cancel(String projectPath, String iid) {
    Watch removed = watches.remove(new Key(projectPath, iid));
    if (removed != null) {
      removed.cancel();
      LOG.info("pipeline watch cancelled: " + projectPath + "!" + iid);
    }
  }

  private void schedule(Project project, Key key, Watch watch, GitLabClient client,
                        int timeoutMinutes, long delayMs) {
    if (watches.get(key) != watch) {
      return;
    }
    try {
      watch.future = SCHEDULER.schedule(
          () -> poll(project, key, watch, client, timeoutMinutes), delayMs, TimeUnit.MILLISECONDS);
    }
    catch (Throwable t) {
      LOG.error("failed to schedule pipeline watch for " + key.projectPath() + "!" + key.iid(), t);
    }
  }

  private void poll(Project project, Key key, Watch watch, GitLabClient client, int timeoutMinutes) {
    // 已被取消或被新的关注替换
    if (watches.get(key) != watch) {
      return;
    }
    if (project.isDisposed()) {
      cancel(key.projectPath(), key.iid());
      return;
    }
    if (System.currentTimeMillis() > watch.deadline) {
      cancel(key.projectPath(), key.iid());
      notifyLater(project,
                  "!" + key.iid() + " 流水线关注已超时（" + timeoutMinutes + " 分钟），已停止。<br>可点「刷新状态」手动查看。",
                  NotificationType.WARNING);
      return;
    }

    try {
      PipelineInfo info = new GitLabService(client).pipelineById(key.projectPath(), watch.pipelineId);
      watch.polls++;
      watch.consecutiveErrors = 0;
      if (info != null) {
        boolean finished = PipelineState.shouldStopWatching(info.status());
        LOG.info("pipeline watch poll: " + key.projectPath() + "!" + key.iid()
            + " pipeline=" + watch.pipelineId + " status=" + info.status()
            + " finished=" + finished + " polls=" + watch.polls);
        // 无论是否结束都推送一次，让界面状态跟真实状态一致
        ApplicationManager.getApplication().invokeLater(() ->
            project.getMessageBus().syncPublisher(TOPIC)
                .pipelineStateChanged(key.iid(), info, finished));
        if (finished) {
          cancel(key.projectPath(), key.iid());
          NotificationType type = switch (PipelineState.classify(info.status())) {
            case SUCCESS -> NotificationType.INFORMATION;
            case FAILURE -> NotificationType.ERROR;
            case NEEDS_MANUAL, UNKNOWN -> NotificationType.WARNING;
            case RUNNING -> NotificationType.INFORMATION;
          };
          notifyLater(project,
                      "!" + key.iid() + " " + PipelineState.watchConclusion(info.status())
                          + (info.webUrl() == null || info.webUrl().isBlank() ? "" : "<br>" + info.webUrl()),
                      type);
          return;
        }
      }
      else {
        LOG.warn("pipeline watch poll returned null for " + key.projectPath() + "!" + key.iid());
      }
    }
    catch (Throwable t) {
      watch.consecutiveErrors++;
      LOG.warn("pipeline watch poll failed for " + key.projectPath() + "!" + key.iid()
                   + " (第 " + watch.consecutiveErrors + " 次)", t);
      // 只提示第一次：避免刷屏，但不再静默（此前要等到超时才暴露）
      if (watch.consecutiveErrors == 1) {
        notifyLater(project,
                    "!" + key.iid() + " 流水线状态轮询失败，将持续重试："
                        + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()),
                    NotificationType.WARNING);
      }
    }
    schedule(project, key, watch, client, timeoutMinutes, POLL_MS);
  }

  /** 通知统一在 EDT 发出，避免后台线程导致的静默失败。 */
  private void notifyLater(Project project, String html, NotificationType type) {
    ApplicationManager.getApplication().invokeLater(() -> notifyNow(project, html, type));
  }

  private void notifyNow(Project project, String html, NotificationType type) {
    try {
      NotificationGroupManager.getInstance()
          .getNotificationGroup(NOTIFICATION_GROUP)
          .createNotification("GitLab+ 流水线", html, type)
          .notify(project);
    }
    catch (Throwable t) {
      // 通知异常不应影响关注生命周期，但必须留下痕迹
      LOG.error("failed to show pipeline notification", t);
    }
  }

  /**
   * 插件卸载/应用关闭时清理。
   *
   * <p>静态调度器必须在这里停掉：否则插件重载后旧线程仍在跑，
   * 会继续抓着旧 Project 的对象直到超时，IDE 也会报 "static thread is still alive"。</p>
   */
  @Override
  public void dispose() {
    for (Key key : new ArrayList<>(watches.keySet())) {
      cancel(key.projectPath(), key.iid());
    }
    watches.clear();
    SCHEDULER.shutdownNow();
  }
}
