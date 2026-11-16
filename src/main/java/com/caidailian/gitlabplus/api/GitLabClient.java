package com.caidailian.gitlabplus.api;

import com.caidailian.gitlabplus.i18n.GitLabPlusBundle;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * 极简 GitLab GraphQL 客户端：单端点 {@code /api/graphql}，PAT 认证，JSON 进出。
 *
 * <p>认证方式与内置插件一致（请求头 <code>PRIVATE-TOKEN</code>），但不复用其内部类。</p>
 */
public final class GitLabClient {

  /** 连接超时（网络不可达时尽快失败，而不是挂住池化线程）。 */
  private static final int CONNECT_TIMEOUT_MS = 15_000;
  /** 读超时；job trace 这类大响应单独放宽。 */
  private static final int READ_TIMEOUT_MS = 45_000;
  private static final int TRACE_READ_TIMEOUT_MS = 120_000;

  private final String serverUrl;
  private final String token;
  /** /api/v4/user 的用户名缓存（实例级：换 token 会新建实例，自然失效）。 */
  private volatile String cachedUsername;

  public GitLabClient(String serverUrl, @Nullable String token) {
    String normalized = serverUrl == null ? "" : serverUrl.trim();
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    this.serverUrl = normalized;
    this.token = token;
  }

  /** 归一化后的服务器地址（用作能力缓存 key）。 */
  public String serverUrl() {
    return serverUrl;
  }

  /**
   * 执行一次 GraphQL 请求，返回根 {@code data} 对象。
   *
   * @throws GitLabApiException 网络失败、HTTP 非 2xx、GraphQL errors 非空
   */
  public JsonObject execute(String query, JsonObject variables) {
    JsonObject payload = new JsonObject();
    payload.addProperty("query", query);
    if (variables != null && !variables.isEmpty()) {
      payload.add("variables", variables);
    }
    String body = payload.toString();
    String endpoint = serverUrl + "/api/graphql";

    String response;
    try {
      response = HttpRequests.post(endpoint, "application/json")
          .isReadResponseOnError(true)
          .throwStatusCodeException(false)
          .tuner(connection -> {
            connection.addRequestProperty("Accept", "application/json");
            if (token != null && !token.isBlank()) {
              connection.addRequestProperty("PRIVATE-TOKEN", token);
            }
          })
          .connect(request -> {
            request.write(body);
            return request.readString();
          });
    }
    catch (IOException e) {
      throw new GitLabApiException(GitLabPlusBundle.message("error.requestFailed", e.getMessage()), e);
    }

    if (response == null || response.isBlank()) {
      throw new GitLabApiException(GitLabPlusBundle.message("error.emptyResponse"));
    }

    JsonObject root;
    try {
      JsonElement parsed = JsonParser.parseString(response);
      if (!parsed.isJsonObject()) {
        throw new GitLabApiException(
            GitLabPlusBundle.message("error.nonJson", JsonSupport.abbreviate(response, 400)));
      }
      root = parsed.getAsJsonObject();
    }
    catch (GitLabApiException e) {
      throw e;
    }
    catch (RuntimeException e) {
      throw new GitLabApiException(
          GitLabPlusBundle.message("error.parseFailed", JsonSupport.abbreviate(response, 400)), e);
    }

    JsonArray errors = JsonSupport.arr(root, "errors");
    String errorText = JsonSupport.errorMessages(errors);
    if (errorText != null) {
      throw new GitLabApiException(GitLabPlusBundle.message("error.graphql", errorText));
    }

    JsonObject data = JsonSupport.obj(root, "data");
    if (data == null) {
      throw new GitLabApiException(
          GitLabPlusBundle.message("error.missingData", JsonSupport.abbreviate(response, 400)));
    }
    return data;
  }

  /** REST GET（诊断、列表、详情共用）。 */
  public JsonObject restGet(String path) {
    return parseObject(restCall("GET", path, null), path);
  }

  /** REST POST（approve / unapprove 等无业务体）。 */
  public JsonObject restPost(String path) {
    return parseObject(restCall("POST", path, "{}"), path);
  }

  /** REST POST（带 JSON 体）。 */
  public JsonObject restPost(String path, JsonObject body) {
    return parseObject(restCall("POST", path, body == null ? "{}" : body.toString()), path);
  }

  /** REST POST（query 形式——GitLab 的 award_emoji 等端点只认 query）。 */
  public JsonObject restPostQuery(String path, java.util.Map<String, String> params) {
    return parseObject(restCall("POST", path + queryString(params), null), path);
  }

  /** REST PUT（query 形式——GitLab 的 discussions resolve 只认 query）。 */
  public JsonObject restPutQuery(String path, java.util.Map<String, String> params) {
    return parseObject(restCall("PUT", path + queryString(params), null), path);
  }

  private static String queryString(java.util.Map<String, String> params) {
    StringBuilder query = new StringBuilder();
    if (params != null) {
      for (var entry : params.entrySet()) {
        if (query.length() > 0) {
          query.append('&');
        }
        query.append(java.net.URLEncoder.encode(entry.getKey(), java.nio.charset.StandardCharsets.UTF_8))
            .append('=')
            .append(java.net.URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(),
                                               java.nio.charset.StandardCharsets.UTF_8));
      }
    }
    return query.isEmpty() ? "" : "?" + query;
  }

  /** REST PUT（带 JSON 体）。 */
  public JsonObject restPut(String path, JsonObject body) {
    return parseObject(restCall("PUT", path, body == null ? "{}" : body.toString()), path);
  }

  /** REST DELETE。 */
  public void restDelete(String path) {
    restCall("DELETE", path, null);
  }


  /** 返回原始 JSON 元素（可能是数组，如 discussions 列表）。 */
  public JsonElement restJsonElement(String method, String path, @Nullable JsonObject body) {
    String response = restCall(method, path, body == null ? null : body.toString());
    if (response == null || response.isBlank()) {
      return new JsonObject();
    }
    try {
      return JsonParser.parseString(response);
    }
    catch (RuntimeException e) {
      throw new GitLabApiException(
          "解析 REST 响应失败：" + JsonSupport.abbreviate(response, 200) + " @ " + path, e);
    }
  }

  private JsonObject parseObject(String response, String path) {
    if (response == null || response.isBlank()) {
      return new JsonObject();
    }
    try {
      JsonElement parsed = JsonParser.parseString(response);
      return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
    }
    catch (RuntimeException e) {
      throw new GitLabApiException(
          "解析 REST 响应失败：" + JsonSupport.abbreviate(response, 200) + " @ " + path, e);
    }
  }

  /** 统一 REST 调用：方法 + 路径 + 可选 JSON 体。 */
  private String restCall(String method, String path, @Nullable String jsonBody) {
    String url = serverUrl + path;
    try {
      com.intellij.util.io.RequestBuilder builder = switch (method) {
        case "GET" -> HttpRequests.request(url);
        case "POST" -> HttpRequests.post(url, "application/json");
        case "PUT" -> HttpRequests.put(url, "application/json");
        case "PATCH" -> HttpRequests.patch(url, "application/json");
        case "DELETE" -> HttpRequests.delete(url);
        default -> throw new GitLabApiException(
            GitLabPlusBundle.message("error.unsupportedMethod", method));
      };
      return builder.accept("application/json")
          .tuner(connection -> {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.addRequestProperty("Accept", "application/json");
            if (token != null && !token.isBlank()) {
              connection.addRequestProperty("PRIVATE-TOKEN", token);
            }
          })
          .connect(request -> {
            if (jsonBody != null) {
              request.write(jsonBody);
            }
            return request.readString();
          });
    }
    catch (HttpRequests.HttpStatusException e) {
      throw new GitLabApiException(GitLabPlusBundle.message("error.http", e.getStatusCode(), path),
                                    e.getStatusCode(), e);
    }
    catch (IOException e) {
      throw new GitLabApiException(GitLabPlusBundle.message("error.requestFailed", e.getMessage()),
                                    0, e);
    }
  }

  /** GET 纯文本响应（job trace 等）：不走 JSON 解析。 */
  public String restGetText(String path) {
    String url = serverUrl + path;
    try {
      return HttpRequests.request(url)
          .accept("text/plain")
          .tuner(connection -> {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(TRACE_READ_TIMEOUT_MS);
            connection.addRequestProperty("Accept", "*/*");
            if (token != null && !token.isBlank()) {
              connection.addRequestProperty("PRIVATE-TOKEN", token);
            }
          })
          .connect(request -> request.readString());
    }
    catch (HttpRequests.HttpStatusException e) {
      throw new GitLabApiException(GitLabPlusBundle.message("error.http", e.getStatusCode(), path),
                                    e.getStatusCode(), e);
    }
    catch (IOException e) {
      throw new GitLabApiException(GitLabPlusBundle.message("error.requestFailed", e.getMessage()),
                                    0, e);
    }
  }

  /**
   * 当前 token 对应的用户名（带缓存）。
   *
   * <p>点赞 toggle 需要知道“我是否已经点过”，因此必须能识别自己的用户名；
   * 换 token 时缓存会失效。</p>
   */
  public String currentUsername() {
    String cached = cachedUsername;
    if (cached != null) {
      return cached;
    }
    JsonObject user = restGet("/api/v4/user");
    String name = JsonSupport.string(user, "username", "");
    cachedUsername = name;
    return name;
  }

  /** GraphQL / 网络层失败。消息面向用户，可直接展示。 */
  public static final class GitLabApiException extends RuntimeException {
    private final int statusCode;

    public GitLabApiException(String message) {
      this(message, 0, null);
    }

    public GitLabApiException(String message, Throwable cause) {
      this(message, 0, cause);
    }

    public GitLabApiException(String message, int statusCode, Throwable cause) {
      super(message, cause);
      this.statusCode = statusCode;
    }

    /** HTTP 状态码；0 表示非 HTTP 错误（网络 / 解析 / GraphQL）。 */
    public int statusCode() {
      return statusCode;
    }
  }
}
