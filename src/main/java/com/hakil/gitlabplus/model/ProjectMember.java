package com.hakil.gitlabplus.model;

import com.hakil.gitlabplus.api.JsonSupport;
import com.google.gson.JsonObject;

/**
 * 项目成员（审阅人候选）。
 *
 * <p>只保留选人需要的字段：{@code username} 是设置审阅人时提交的值，
 * {@code name} 用于展示。</p>
 */
public record ProjectMember(String username, String name) {

  public static ProjectMember from(JsonObject node) {
    return new ProjectMember(
        JsonSupport.string(node, "username", ""),
        JsonSupport.string(node, "name", ""));
  }

  /** 下拉/列表里的展示文本：有显示名时带上用户名，方便同名区分。 */
  public String display() {
    if (username == null || username.isBlank()) {
      return name == null ? "" : name;
    }
    if (name == null || name.isBlank() || name.equals(username)) {
      return username;
    }
    return name + "  (@" + username + ")";
  }

  @Override
  public String toString() {
    return display();
  }
}
