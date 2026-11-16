package com.caidailian.gitlabplus.git;

import com.intellij.openapi.project.Project;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 IDEA 中的 Git 仓库推导 GitLab 服务器与项目全路径。
 *
 * <p>只读 <code>.git/config</code> 的 remote url，不依赖 GitRemote API，
 * 支持 scp 风格（<code>git@host:group/proj.git</code>）、
 * <code>ssh://</code>、<code>http(s)://</code> 三种形态。</p>
 */
public final class GitProjectResolver {

  private static final Pattern URL_LINE = Pattern.compile("(?m)^\\s*url\\s*=\\s*(.+?)\\s*$");

  private GitProjectResolver() {
  }

  /** 解析结果：GitLab 基地址 + 项目全路径（group/sub/project）。 */
  public record Resolved(String serverUrl, String projectPath, String remoteUrl) {
  }

  /**
   * 依次尝试项目内每个 Git 仓库的 remote，返回第一个可解析为 GitLab 项目的结果。
   */
  public static @Nullable Resolved resolve(Project project) {
    List<GitRepository> repositories = GitRepositoryManager.getInstance(project).getRepositories();
    for (GitRepository repository : repositories) {
      for (String remoteUrl : readRemoteUrls(repository)) {
        Resolved resolved = parseRemoteUrl(remoteUrl);
        if (resolved != null) {
          return resolved;
        }
      }
    }
    return null;
  }

  /**
   * 枚举当前 IDEA 项目里所有 Git 仓库可解析出的 GitLab 项目（按 server+path 去重）。
   *
   * <p>用于工具窗口的项目下拉：一个多模块工程可能含多个仓库/多个 remote。</p>
   */
  public static List<Resolved> resolveAll(Project project) {
    List<Resolved> result = new java.util.ArrayList<>();
    java.util.Set<String> seen = new java.util.LinkedHashSet<>();
    for (GitRepository repository : GitRepositoryManager.getInstance(project).getRepositories()) {
      for (String remoteUrl : readRemoteUrls(repository)) {
        Resolved resolved = parseRemoteUrl(remoteUrl);
        if (resolved == null) {
          continue;
        }
        String key = resolved.serverUrl() + '|' + resolved.projectPath();
        if (seen.add(key)) {
          result.add(resolved);
        }
      }
    }
    return result;
  }

  /** 读取仓库 .git/config 里所有 remote url（供分支/项目解析复用）。 */
  public static List<String> readRemoteUrls(GitRepository repository) {
    File config;
    try {
      config = repository.getRepositoryFiles().getConfigFile();
    }
    catch (Exception e) {
      return List.of();
    }
    if (config == null || !config.isFile()) {
      return List.of();
    }
    String text;
    try {
      text = Files.readString(config.toPath(), StandardCharsets.UTF_8);
    }
    catch (Exception e) {
      return List.of();
    }
    List<String> urls = new java.util.ArrayList<>();
    Matcher matcher = URL_LINE.matcher(text);
    while (matcher.find()) {
      String value = matcher.group(1);
      if (value != null && !value.isBlank()) {
        urls.add(value);
      }
    }
    return urls;
  }

  /**
   * 把 remote url 解析为 GitLab 地址与项目路径；非远端形态（本地路径、file://）返回 null。
   */
  public static @Nullable Resolved parseRemoteUrl(String remoteUrl) {
    if (remoteUrl == null) {
      return null;
    }
    String url = remoteUrl.trim();
    if (url.isEmpty() || url.startsWith("file://")) {
      return null;
    }

    String hostPart;
    String pathPart;
    String serverUrl;

    int schemeIndex = url.indexOf("://");
    if (schemeIndex > 0) {
      String scheme = url.substring(0, schemeIndex).toLowerCase();
      String rest = url.substring(schemeIndex + 3);
      int slash = rest.indexOf('/');
      if (slash < 0) {
        return null;
      }
      hostPart = stripUserInfo(rest.substring(0, slash));
      pathPart = rest.substring(slash + 1);
      if ("http".equals(scheme) || "https".equals(scheme)) {
        serverUrl = scheme + "://" + hostPart;
      }
      else {
        // ssh / git 协议：映射到同主机的 https，端口不沿用
        serverUrl = "https://" + stripPort(hostPart);
      }
    }
    else {
      int colon = url.indexOf(':');
      if (colon < 0) {
        return null;
      }
      String beforeColon = url.substring(0, colon);
      if (beforeColon.contains("/") || beforeColon.contains("\\")) {
        // 本地路径
        return null;
      }
      if (beforeColon.length() == 1 && Character.isLetter(beforeColon.charAt(0))) {
        // Windows 盘符形式 C:/path
        return null;
      }
      hostPart = stripUserInfo(beforeColon);
      pathPart = url.substring(colon + 1);
      serverUrl = "https://" + stripPort(hostPart);
    }

    pathPart = pathPart.replaceAll("^/+", "");
    if (pathPart.endsWith("/")) {
      pathPart = pathPart.substring(0, pathPart.length() - 1);
    }
    if (pathPart.endsWith(".git")) {
      pathPart = pathPart.substring(0, pathPart.length() - 4);
    }
    if (pathPart.isEmpty() || hostPart.isEmpty()) {
      return null;
    }
    return new Resolved(serverUrl, pathPart, url);
  }

  private static String stripUserInfo(String hostPart) {
    int at = hostPart.lastIndexOf('@');
    return at >= 0 ? hostPart.substring(at + 1) : hostPart;
  }

  private static String stripPort(String hostPart) {
    if (hostPart.startsWith("[")) {
      int close = hostPart.indexOf(']');
      return close > 0 ? hostPart.substring(0, close + 1) : hostPart;
    }
    int colon = hostPart.lastIndexOf(':');
    return colon > 0 ? hostPart.substring(0, colon) : hostPart;
  }
}
