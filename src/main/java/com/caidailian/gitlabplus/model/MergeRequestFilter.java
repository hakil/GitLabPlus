package com.caidailian.gitlabplus.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MR 列表的纯函数：客户端搜索过滤、分页去重、UI 状态到 GraphQL 枚举的映射。
 *
 * <p>与 Swing / IDE 无关，便于直接单测。</p>
 */
public final class MergeRequestFilter {

  /** UI 下拉可选值（顺序即展示顺序）。 */
  public static final List<String> STATES = List.of("all", "opened", "merged", "closed");

  private MergeRequestFilter() {
  }

  /**
   * 客户端搜索：匹配 iid、标题、源分支、目标分支，忽略大小写。
   *
   * @param text 为空 / 空白时原样返回
   */
  public static List<MergeRequestInfo> filter(List<MergeRequestInfo> rows, String text) {
    if (text == null || text.isBlank()) {
      return new ArrayList<>(rows);
    }
    String needle = text.trim().toLowerCase(Locale.ROOT);
    List<MergeRequestInfo> result = new ArrayList<>();
    for (MergeRequestInfo row : rows) {
      if (matches(row, needle)) {
        result.add(row);
      }
    }
    return result;
  }

  private static boolean matches(MergeRequestInfo row, String needle) {
    return contains(row.iid, needle)
        || contains(row.title, needle)
        || contains(row.sourceBranch, needle)
        || contains(row.targetBranch, needle);
  }

  private static boolean contains(String value, String needle) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
  }

  /**
   * 分页合并：按 iid 去重，保留既有顺序，新条目追加在后。
   *
   * <p>同一 MR 在翻页期间被更新时，以既有条目为准（避免列表跳动）。</p>
   */
  public static List<MergeRequestInfo> mergeDistinct(List<MergeRequestInfo> existing,
                                                    List<MergeRequestInfo> incoming) {
    Map<String, MergeRequestInfo> byIid = new LinkedHashMap<>();
    if (existing != null) {
      for (MergeRequestInfo row : existing) {
        byIid.putIfAbsent(key(row), row);
      }
    }
    if (incoming != null) {
      for (MergeRequestInfo row : incoming) {
        byIid.putIfAbsent(key(row), row);
      }
    }
    return new ArrayList<>(byIid.values());
  }

  private static String key(MergeRequestInfo row) {
    return row == null || row.iid == null ? "" : row.iid;
  }

  /** UI 状态值直接就是 GraphQL {@code MergeRequestState} 枚举字面量（小写）。 */
  public static String toGraphQLState(String uiState) {
    if (uiState == null || uiState.isBlank() || "all".equalsIgnoreCase(uiState)) {
      return null;
    }
    return uiState.trim().toLowerCase(Locale.ROOT);
  }
}
