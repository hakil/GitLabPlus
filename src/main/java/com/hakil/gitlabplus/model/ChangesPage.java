package com.hakil.gitlabplus.model;

import com.hakil.gitlabplus.api.JsonSupport;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * 一个 MR 的变更快照：diff 三元 sha + 逐文件变更。
 */
public record ChangesPage(DiffRefs diffRefs, List<DiffParser.FileDiff> files) {

  /** 行内评论 position 所需的三个 sha。 */
  public record DiffRefs(String baseSha, String startSha, String headSha) {
    public boolean complete() {
      return notBlank(baseSha) && notBlank(startSha) && notBlank(headSha);
    }

    /** 缺字段时用备用来源（/versions）补齐；两者都没有则返回原值。 */
    public static DiffRefs merge(DiffRefs primary, DiffRefs fallback) {
      if (fallback == null) {
        return primary;
      }
      if (primary == null) {
        return fallback;
      }
      return new DiffRefs(
          notBlank(primary.baseSha()) ? primary.baseSha() : fallback.baseSha(),
          notBlank(primary.startSha()) ? primary.startSha() : fallback.startSha(),
          notBlank(primary.headSha()) ? primary.headSha() : fallback.headSha());
    }

    private static boolean notBlank(String value) {
      return value != null && !value.isBlank();
    }
  }

  public static ChangesPage from(JsonObject response) {
    JsonObject refs = JsonSupport.obj(response, "diff_refs");
    DiffRefs diffRefs = new DiffRefs(
        JsonSupport.string(refs, "base_sha", ""),
        JsonSupport.string(refs, "start_sha", ""),
        JsonSupport.string(refs, "head_sha", ""));
    List<DiffParser.FileDiff> files = JsonSupport.arr(response, "changes") == null
        ? List.of()
        : DiffParser.parseChanges(JsonSupport.arr(response, "changes"));
    return new ChangesPage(diffRefs, files);
  }

  public int totalAdded() {
    int sum = 0;
    for (DiffParser.FileDiff file : files) {
      sum += file.addedLines();
    }
    return sum;
  }

  public int totalRemoved() {
    int sum = 0;
    for (DiffParser.FileDiff file : files) {
      sum += file.removedLines();
    }
    return sum;
  }
}
