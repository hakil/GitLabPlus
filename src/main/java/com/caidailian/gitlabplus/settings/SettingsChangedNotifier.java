package com.caidailian.gitlabplus.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.Topic;

/**
 * 设置变更广播。
 *
 * <p>设置页 {@code apply()} 之后，工具窗口持有的 GitLabClient 仍是旧配置；
 * 不广播的话用户会觉得「改了不起作用，必须手动点刷新」。这里用应用级总线通知，
 * 订阅方自行重建 client 并重载。</p>
 */
public final class SettingsChangedNotifier {

  public interface Listener {
    void settingsChanged();
  }

  public static final Topic<Listener> TOPIC =
      Topic.create("GitLabPlus.SettingsChanged", Listener.class);

  private SettingsChangedNotifier() {
  }

  public static void fireChanged() {
    ApplicationManager.getApplication().getMessageBus()
        .syncPublisher(TOPIC)
        .settingsChanged();
  }
}
