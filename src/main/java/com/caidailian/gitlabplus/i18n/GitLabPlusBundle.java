package com.caidailian.gitlabplus.i18n;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * 界面文案的资源束入口。
 *
 * <p>默认资源（{@code GitLabPlusBundle.properties}）为英文，中文翻译在
 * {@code GitLabPlusBundle_zh_CN.properties}。具体用哪份由 {@link LanguageService}
 * 决定：默认跟随 JVM，也可在设置里固定为中文或英文。</p>
 *
 * <p>约定：技术名词（GitLab、MR、CI、SHA、Job、Stage、Diff）保留英文，
 * 其余界面用语一律走资源束，避免同一个界面中英混排。</p>
 */
public final class GitLabPlusBundle {

  private static final String BASE_NAME = "messages.GitLabPlusBundle";

  /**
   * 禁用跨语言回退。
   *
   * <p>{@link ResourceBundle.Control#getFallbackLocale} 默认返回 JVM 的默认 Locale：
   * 当我们显式请求英文而仓库里没有 {@code _en.properties} 时，会“回退”到
   * {@code _zh_CN}，导致「切成英文仍显示中文」。这里返回 null 彻底关掉该行为，
   * 使 {@code en} 直接落在默认资源（英文）上。</p>
   */
  private static final ResourceBundle.Control NO_FALLBACK = new ResourceBundle.Control() {
    @Override
    public java.util.Locale getFallbackLocale(String baseName, java.util.Locale locale) {
      return null;
    }
  };

  private GitLabPlusBundle() {
  }

  /**
   * 取文案。
   *
   * @param key    资源键
   * @param params MessageFormat 参数（可为空）
   * @return 文案；缺键时返回 {@code key} 本身，便于一眼发现漏配
   */
  public static String message(String key, Object... params) {
    return messageFor(locale(), key, params);
  }

  /** 指定 Locale 取文案（供自测验证英文/中文分别命中）。 */
  public static String messageFor(java.util.Locale locale, String key, Object... params) {
    String pattern;
    try {
      pattern = ResourceBundle.getBundle(BASE_NAME, locale == null ? java.util.Locale.getDefault() : locale,
                                         NO_FALLBACK)
          .getString(key);
    }
    catch (MissingResourceException e) {
      return key;
    }
    if (params == null || params.length == 0) {
      return pattern;
    }
    return MessageFormat.format(pattern, params);
  }

  private static java.util.Locale locale() {
    try {
      LanguageService service = LanguageService.getInstance();
      if (service != null) {
        return service.currentLocale();
      }
    }
    catch (Throwable ignored) {
      // 应用尚未初始化（单元自测等场景）或服务未注册时退回 JVM 默认
    }
    return java.util.Locale.getDefault();
  }
}
