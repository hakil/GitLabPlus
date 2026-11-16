package com.caidailian.gitlabplus.settings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 从内置 GitLab 插件导入已登录账户的 Access Token。
 *
 * <p>实现依据（反编译确认，非猜测）：</p>
 * <ul>
 *   <li>账户元数据：{@code @State(name="GitLabAccounts", storages=@Storage("gitlab.xml"))}
 *       → {@code <config>/options/gitlab.xml} 中 {@code <component name="GitLabAccounts">} 的 JSON：
 *       {@code {"accounts":[{"id":"...","name":"...","server":{"uri":"https://..."}}]}}</li>
 *   <li>凭据键：{@code PasswordSafeCredentialsRepository("GitLab", mapper)}
 *       内部为 {@code CredentialAttributes(generateServiceName("GitLab", account.id))}</li>
 *   <li>凭据值：{@code GitLabCredentials} 的 kotlinx 序列化 JSON（含 {@code accessToken}）；
 *       旧格式退化为裸 token 字符串</li>
 * </ul>
 *
 * <p>不读取、不解密任何凭据文件：统一经 {@link PasswordSafe} 由平台解密。</p>
 */
public final class OfficialGitLabTokenImporter {

  private static final Logger LOG = Logger.getInstance(OfficialGitLabTokenImporter.class);
  private static final String SERVICE_NAME = "GitLab";
  private static final String ACCOUNTS_COMPONENT = "GitLabAccounts";

  private OfficialGitLabTokenImporter() {
  }

  /** 内置插件中的一个已登录账户。 */
  public record ImportedAccount(String accountId, String accountName, String serverUrl, String token) {
  }

  /**
   * 枚举内置插件账户并读取其 token。
   *
   * <p>无账户、凭据不可读、或未启用该插件时返回空列表，不抛异常。</p>
   */
  public static List<ImportedAccount> importAccounts() {
    List<ImportedAccount> result = new ArrayList<>();
    JsonArray accounts = readAccountsState();
    if (accounts == null) {
      return result;
    }
    for (JsonElement element : accounts) {
      if (element == null || !element.isJsonObject()) {
        continue;
      }
      JsonObject account = element.getAsJsonObject();
      String id = stringOf(account, "id");
      String name = stringOf(account, "name");
      String serverUrl = "";
      if (account.has("server") && account.get("server").isJsonObject()) {
        serverUrl = stringOf(account.getAsJsonObject("server"), "uri");
      }
      if (StringUtil.isEmpty(id)) {
        continue;
      }
      String token = readToken(id);
      if (StringUtil.isEmpty(token)) {
        LOG.info("No credentials stored for GitLab account '" + name + "' (" + id + ")");
        continue;
      }
      result.add(new ImportedAccount(id, name, serverUrl, token));
    }
    return result;
  }

  /** 读取 {@code <config>/options/gitlab.xml} 里的账户数组；缺失返回 null。 */
  private static @Nullable JsonArray readAccountsState() {
    try {
      Path optionsDir = Paths.get(com.intellij.openapi.application.PathManager.getOptionsPath());
      File configFile = optionsDir.resolve("gitlab.xml").toFile();
      if (!configFile.isFile()) {
        return null;
      }
      String content = FileUtil.loadFile(configFile, StandardCharsets.UTF_8);
      var root = JDOMUtil.load(content);
      for (var component : root.getChildren("component")) {
        if (!ACCOUNTS_COMPONENT.equals(component.getAttributeValue("name"))) {
          continue;
        }
        String json = component.getText();
        if (StringUtil.isEmptyOrSpaces(json)) {
          return null;
        }
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) {
          return null;
        }
        JsonObject state = parsed.getAsJsonObject();
        if (state.has("accounts") && state.get("accounts").isJsonArray()) {
          return state.getAsJsonArray("accounts");
        }
      }
    }
    catch (Throwable t) {
      LOG.warn("Failed to read GitLab accounts state", t);
    }
    return null;
  }

  /** 按内置插件的键规则从 PasswordSafe 取 token。 */
  private static @Nullable String readToken(String accountId) {
    try {
      String serviceName = CredentialAttributesKt.generateServiceName(SERVICE_NAME, accountId);
      CredentialAttributes attributes = new CredentialAttributes(serviceName);
      String stored = PasswordSafe.getInstance().getPassword(attributes);
      if (StringUtil.isEmptyOrSpaces(stored)) {
        return null;
      }
      return extractToken(stored);
    }
    catch (Throwable t) {
      LOG.warn("Failed to read GitLab credentials for account " + accountId, t);
      return null;
    }
  }

  /**
   * 凭据内容既可能是 kotlinx 序列化的 JSON（{@code {"accessToken":"..."}}），
   * 也可能是旧格式的裸 token。
   */
  public static String extractToken(String stored) {
    String trimmed = stored.trim();
    if (trimmed.startsWith("{")) {
      try {
        JsonElement parsed = JsonParser.parseString(trimmed);
        if (parsed.isJsonObject()) {
          JsonObject object = parsed.getAsJsonObject();
          if (object.has("accessToken") && object.get("accessToken").isJsonPrimitive()) {
            return object.get("accessToken").getAsString();
          }
        }
      }
      catch (RuntimeException ignored) {
        // 不是 JSON，按裸 token 处理
      }
    }
    return trimmed;
  }

  private static String stringOf(JsonObject object, String key) {
    if (object == null || !object.has(key)) {
      return "";
    }
    JsonElement element = object.get(key);
    return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
  }
}
