package com.caidailian.gitlabplus.i18n;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * 界面语言设置。
 *
 * <p>独立于 GitLab 配置持久化（{@code gitlab-plus-language.xml}）：
 * 语言是「怎么显示」，与「连哪个 GitLab」无关，放在一起会让设置页耦合。</p>
 *
 * <p>取值：{@code auto}（默认，跟随 JVM）/ {@code zh} / {@code en}。</p>
 */
@State(name = "GitLabPlusLanguage", storages = @Storage("gitlab-plus-language.xml"))
public final class LanguageService implements PersistentStateComponent<LanguageService.State> {

  public static final String AUTO = "auto";
  public static final String ZH = "zh";
  public static final String EN = "en";

  public static final class State {
    public String language = AUTO;
  }

  private State state = new State();

  public static LanguageService getInstance() {
    return ApplicationManager.getApplication().getService(LanguageService.class);
  }

  @Override
  public @NotNull State getState() {
    return state;
  }

  @Override
  public void loadState(@NotNull State state) {
    this.state = state;
  }

  public String getLanguage() {
    String value = state.language;
    if (ZH.equals(value) || EN.equals(value)) {
      return value;
    }
    return AUTO;
  }

  public void setLanguage(String language) {
    state.language = ZH.equals(language) || EN.equals(language) ? language : AUTO;
  }

  /** 当前生效的 Locale（auto 时用 JVM 默认）。 */
  public Locale currentLocale() {
    return switch (getLanguage()) {
      case ZH -> Locale.SIMPLIFIED_CHINESE;
      case EN -> Locale.ENGLISH;
      default -> Locale.getDefault();
    };
  }
}
