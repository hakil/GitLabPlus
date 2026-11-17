package com.hakil.gitlabplus.settings;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.ide.passwordSafe.PasswordSafe;
import org.jetbrains.annotations.Nullable;

/**
 * Access Token 的存取：交给 IDEA 凭据库（PasswordSafe），不写入普通配置 XML。
 */
public final class GitLabTokenStore {

  private static final String SERVICE_PREFIX = "GitLabPlus:";

  private GitLabTokenStore() {
  }

  private static CredentialAttributes attributes(String serverUrl) {
    String normalized = serverUrl == null ? "" : serverUrl.trim();
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return new CredentialAttributes(SERVICE_PREFIX + normalized);
  }

  public static @Nullable String getToken(@Nullable String serverUrl) {
    if (serverUrl == null || serverUrl.isBlank()) {
      return null;
    }
    String password = PasswordSafe.getInstance().getPassword(attributes(serverUrl));
    if (password == null) {
      return null;
    }
    // 从网页复制时常带入首尾空白/换行，必须清理
    password = password.trim();
    return password.isEmpty() ? null : password;
  }

  public static void setToken(@Nullable String serverUrl, @Nullable String token) {
    if (serverUrl == null || serverUrl.isBlank()) {
      return;
    }
    String clean = token == null ? null : token.trim();
    PasswordSafe.getInstance().setPassword(attributes(serverUrl), clean == null || clean.isEmpty() ? null : clean);
  }
}
