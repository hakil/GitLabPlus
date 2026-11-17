package com.hakil.gitlabplus.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 新建 MR 时源分支 / 目标分支的排序与默认值推荐。
 *
 * <p>目标：把「每次都手动挑分支」降为「直接确认」。规则贴近常见 git flow：</p>
 * <ul>
 *   <li>{@code feature/*}、{@code feat/*} → dev / develop</li>
 *   <li>{@code bugfix/*}、{@code bug/*} → dev</li>
 *   <li>{@code hotfix/*}、{@code fix/*} → master / main（生产热修）</li>
 *   <li>{@code release/*} → master / main</li>
 * </ul>
 *
 * <p>排序优先级：场景推荐 → 上次使用 → 主干分支优先级 → 其余（保持原序）。</p>
 */
public final class BranchOrdering {

  /** 主干分支优先级（与具体分支名匹配，命中越靠前越优先）。 */
  private static final List<String> TRUNK_PRIORITY = List.of(
      "dev", "develop", "development", "test", "testing", "uat", "staging",
      "master", "main", "prod", "production");

  private BranchOrdering() {
  }

  /**
   * 源分支排序：当前分支置顶，其余保持原有顺序（稳定性由 {@link List#sort} 保证）。
   */
  public static List<String> orderSources(List<String> candidates, String currentBranch) {
    List<String> result = new ArrayList<>(candidates == null ? List.of() : candidates);
    if (currentBranch == null || currentBranch.isBlank()) {
      return result;
    }
    result.sort((left, right) -> Integer.compare(
        left.equals(currentBranch) ? 0 : 1,
        right.equals(currentBranch) ? 0 : 1));
    return result;
  }

  /**
   * 目标分支排序：场景推荐 → 上次使用 → 主干优先级 → 其余。
   */
  public static List<String> orderTargets(List<String> candidates, String lastUsed, String sourceBranch) {
    List<String> result = new ArrayList<>(candidates == null ? List.of() : candidates);
    List<String> suggestions = suggestedTargets(sourceBranch);
    result.sort((left, right) -> Integer.compare(
        rank(left, lastUsed, suggestions), rank(right, lastUsed, suggestions)));
    return result;
  }

  /**
   * 默认目标分支：场景推荐（且实际存在）→ 上次使用（仍存在）→ 列表第一个。
   */
  public static String pickTarget(List<String> orderedTargets, String lastUsed, String sourceBranch) {
    if (orderedTargets == null || orderedTargets.isEmpty()) {
      return "";
    }
    for (String suggestion : suggestedTargets(sourceBranch)) {
      if (orderedTargets.contains(suggestion)) {
        return suggestion;
      }
    }
    if (lastUsed != null && !lastUsed.isBlank() && orderedTargets.contains(lastUsed)) {
      return lastUsed;
    }
    return orderedTargets.get(0);
  }

  /** 按源分支前缀给出的推荐目标分支（按优先级）。 */
  public static List<String> suggestedTargets(String sourceBranch) {
    String branch = sourceBranch == null ? "" : sourceBranch.trim().toLowerCase();
    if (branch.startsWith("hotfix/") || branch.startsWith("hotfix-")
        || branch.startsWith("fix/") || branch.startsWith("fix-")) {
      return List.of("master", "main", "prod", "production");
    }
    if (branch.startsWith("release/") || branch.startsWith("release-")) {
      return List.of("master", "main");
    }
    if (branch.startsWith("feature/") || branch.startsWith("feature-")
        || branch.startsWith("feat/") || branch.startsWith("feat-")) {
      return List.of("dev", "develop", "development");
    }
    if (branch.startsWith("bugfix/") || branch.startsWith("bugfix-")
        || branch.startsWith("bug/") || branch.startsWith("bug-")) {
      return List.of("dev", "develop");
    }
    return List.of("dev", "develop", "master", "main");
  }

  /** 排序权重（越小越靠前）。 */
  private static int rank(String branch, String lastUsed, List<String> suggestions) {
    if (branch == null) {
      return Integer.MAX_VALUE;
    }
    int suggested = suggestions.indexOf(branch);
    if (suggested >= 0) {
      return suggested * 10;
    }
    if (lastUsed != null && lastUsed.equals(branch)) {
      return 100;
    }
    int trunk = TRUNK_PRIORITY.indexOf(branch);
    if (trunk >= 0) {
      return 200 + trunk;
    }
    return 1000;
  }
}
