package com.caidailian.gitlabplus.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Gson 安全访问工具。
 *
 * <p>Gson 的 {@code JsonObject#getAsJsonObject(String)} / {@code getAsJsonArray(String)}
 * 只做强制类型转换，不做类型判断：当成员是 {@code JsonNull}（GraphQL 会把
 * 「项目不存在 / 无权限 / 字段未授权」直接返回为 null）时抛
 * {@code ClassCastException: JsonNull cannot be cast to JsonObject}。
 * 所有 GraphQL 响应解析必须经这里，不允许直接调用 Gson 的 getAs* 系列。</p>
 */
public final class JsonSupport {

  private JsonSupport() {
  }

  public static JsonObject obj(JsonObject parent, String name) {
    if (parent == null || !parent.has(name)) {
      return null;
    }
    JsonElement element = parent.get(name);
    return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
  }

  public static JsonArray arr(JsonObject parent, String name) {
    if (parent == null || !parent.has(name)) {
      return null;
    }
    JsonElement element = parent.get(name);
    return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
  }

  public static String string(JsonObject parent, String name, String fallback) {
    if (parent == null || !parent.has(name)) {
      return fallback;
    }
    JsonElement element = parent.get(name);
    if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
      return fallback;
    }
    try {
      return element.getAsString();
    }
    catch (RuntimeException e) {
      return fallback;
    }
  }

  public static boolean bool(JsonObject parent, String name, boolean fallback) {
    if (parent == null || !parent.has(name)) {
      return fallback;
    }
    JsonElement element = parent.get(name);
    if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
      return fallback;
    }
    try {
      return element.getAsBoolean();
    }
    catch (RuntimeException e) {
      return fallback;
    }
  }

  /** 拼接 GraphQL {@code errors} 数组为可读消息；null / 空数组返回 null。 */
  public static String errorMessages(JsonArray errors) {
    if (errors == null || errors.isEmpty()) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    for (JsonElement element : errors) {
      if (element == null) {
        continue;
      }
      String text;
      if (element.isJsonObject()) {
        JsonObject errorObject = element.getAsJsonObject();
        text = string(errorObject, "message", errorObject.toString());
      }
      else if (element.isJsonNull()) {
        continue;
      }
      else {
        text = element.toString();
      }
      if (text == null || text.isBlank()) {
        continue;
      }
      if (!sb.isEmpty()) {
        sb.append("; ");
      }
      sb.append(text);
    }
    return sb.isEmpty() ? null : sb.toString();
  }

  public static String abbreviate(String text, int limit) {
    if (text == null) {
      return "";
    }
    return text.length() <= limit ? text : text.substring(0, limit) + "…";
  }
}
