package com.caidailian.gitlabplus.model;

import java.util.Map;

/**
 * 创建 MR 时的标题/描述模板渲染。
 *
 * <p>占位符形如 <code>${source}</code>：未知占位符原样保留（便于用户写错后一眼看出），
 * 值为 null 的占位符替换为空串。</p>
 */
public final class TemplateRenderer {

  /**
   * 默认标题模板：GitLab 惯例的合并标题格式。
   *
   * <p>与官方 IDEA 插件的差异（已反编译核实）：官方创建 MR 时标题/描述都是
   * <b>空的 placeholder</b>（{@code merge.request.create.title.placeholder=Title}），
   * 这里按用户要求预填合并式标题。</p>
   */
  public static final String DEFAULT_TITLE_TEMPLATE = "Merge branch '${source}' into '${target}'";

  /** 默认描述模板：留空（与官方插件一致，描述由用户填写或项目模板提供）。 */
  public static final String DEFAULT_DESCRIPTION_TEMPLATE = "";

  private TemplateRenderer() {
  }

  public static String render(String template, Map<String, String> variables) {
    if (template == null) {
      return "";
    }
    String result = template;
    if (variables != null) {
      for (Map.Entry<String, String> entry : variables.entrySet()) {
        String placeholder = "${" + entry.getKey() + "}";
        String value = entry.getValue() == null ? "" : entry.getValue();
        result = result.replace(placeholder, value);
      }
    }
    return result;
  }

  /**
   * 分支名 -> 可读标题：去掉前缀目录，`-`/`_` 转空格。
   *
   * <p>例：`feature/login-page` → `login page`。</p>
   */
  public static String branchTitle(String branch) {
    if (branch == null || branch.isBlank()) {
      return "";
    }
    String name = branch.contains("/") ? branch.substring(branch.lastIndexOf('/') + 1) : branch;
    return name.replace('-', ' ').replace('_', ' ').trim();
  }
}
