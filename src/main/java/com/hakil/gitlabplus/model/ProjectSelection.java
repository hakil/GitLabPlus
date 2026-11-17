package com.hakil.gitlabplus.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 「项目」下拉的最终顺序计算。
 *
 * <p>设置里维护一份用户挑过、排过序的项目列表；仓库被删除或改名后，
 * 配置里可能残留已失效的路径。这里的规则是：</p>
 * <ul>
 *   <li>配置为空 → 视为未配置，返回全部可用项目（首次开箱即用）</li>
 *   <li>配置非空 → 严格按配置顺序输出，已不存在的项目静默忽略</li>
 * </ul>
 */
public final class ProjectSelection {

  private ProjectSelection() {
  }

  public static List<String> ordered(List<String> configured, List<String> available) {
    List<String> pool = available == null ? List.of() : available;
    if (configured == null || configured.isEmpty()) {
      return new ArrayList<>(pool);
    }
    Set<String> existing = new LinkedHashSet<>(pool);
    List<String> result = new ArrayList<>();
    for (String path : configured) {
      if (path != null && existing.contains(path) && !result.contains(path)) {
        result.add(path);
      }
    }
    return result;
  }

  /** 可用项目中尚未出现在配置里的（「刷新」时要放进左侧列表的新项目）。 */
  public static List<String> newEntries(List<String> configured, List<String> available) {
    Set<String> known = new LinkedHashSet<>(configured == null ? List.of() : configured);
    List<String> fresh = new ArrayList<>();
    for (String path : available == null ? List.<String>of() : available) {
      if (path != null && !known.contains(path) && !fresh.contains(path)) {
        fresh.add(path);
      }
    }
    return fresh;
  }
}
