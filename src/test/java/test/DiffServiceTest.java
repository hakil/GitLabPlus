package test;

import org.junit.jupiter.api.Test;
import com.hakil.gitlabplus.model.ChangesPage;
import org.junit.jupiter.api.Test;
import com.hakil.gitlabplus.model.DiffParser;
import org.junit.jupiter.api.Test;
import com.hakil.gitlabplus.model.SuggestionBuilder;
import org.junit.jupiter.api.Test;
import com.hakil.gitlabplus.service.GitLabDiffService;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import java.util.List;
import org.junit.jupiter.api.Test;
import java.util.Map;

/**
 * P3 服务层自测：position 参数构造、changes 解析、suggestion 生成与应用。
 */
class DiffServiceTest extends SuiteTestSupport {

    @Test
  void suite() {
    String changesJson = """
        {
          "diff_refs": {"base_sha": "B", "start_sha": "S", "head_sha": "H"},
          "changes": [
            {"old_path": "src/A.java", "new_path": "src/A.java",
             "diff": "@@ -1,3 +1,4 @@\\n ctx\\n-old\\n+new\\n+extra\\n ctx2"}
          ]
        }
        """;
    ChangesPage page = ChangesPage.from(obj(changesJson));
    TestAssert.eq("diff_refs 完整", true, page.diffRefs().complete());
    TestAssert.eq("文件数", 1, page.files().size());
    TestAssert.eq("added 总数", 2, page.totalAdded());
    TestAssert.eq("removed 总数", 1, page.totalRemoved());

    DiffParser.FileDiff file = page.files().get(0);
    List<DiffParser.Hunk> hunks = file.hunks();
    DiffParser.DiffLine added = DiffParser.findNewLine(hunks, 2);
    DiffParser.DiffLine context = DiffParser.findNewLine(hunks, 1);

    Map<String, String> addedParams = GitLabDiffService.positionParams(page.diffRefs(), file, added);
    TestAssert.eq("新增行 base_sha", "B", addedParams.get("position[base_sha]"));
    TestAssert.eq("新增行 start_sha", "S", addedParams.get("position[start_sha]"));
    TestAssert.eq("新增行 head_sha", "H", addedParams.get("position[head_sha]"));
    TestAssert.eq("position_type", "text", addedParams.get("position[position_type]"));
    TestAssert.eq("新增行有 new_line", "2", addedParams.get("position[new_line]"));
    TestAssert.eq("新增行无 old_line", null, addedParams.get("position[old_line]"));

    Map<String, String> contextParams = GitLabDiffService.positionParams(page.diffRefs(), file, context);
    TestAssert.eq("上下文行有 new_line", "1", contextParams.get("position[new_line]"));
    TestAssert.eq("上下文行有 old_line", "1", contextParams.get("position[old_line]"));

    DiffParser.DiffLine removed = hunks.get(0).lines().stream()
        .filter(l -> l.kind() == DiffParser.DiffLine.Kind.REMOVED)
        .findFirst().orElseThrow();
    Map<String, String> removedParams = GitLabDiffService.positionParams(page.diffRefs(), file, removed);
    TestAssert.eq("删除行有 old_line", "2", removedParams.get("position[old_line]"));
    TestAssert.eq("删除行无 new_line", null, removedParams.get("position[new_line]"));

    boolean threw = false;
    try {
      GitLabDiffService.positionParams(new ChangesPage.DiffRefs("", "", ""), file, added);
    }
    catch (RuntimeException e) {
      threw = true;
    }
    TestAssert.eq("sha 缺失时 fail-fast", true, threw);

    TestAssert.eq("按 new_path 找文件", "src/A.java", GitLabDiffService.findFile(page, "src/A.java").newPath());
    TestAssert.eq("未找到返回 null", null, GitLabDiffService.findFile(page, "nope.java"));

    TestAssert.eq("suggestion 块格式",
                      "```suggestion:-1+2\nX\n```",
                      SuggestionBuilder.build("X", 1, 2));
    int[] ctx = SuggestionBuilder.defaultContext(hunks.get(0).lines(), 2);
    TestAssert.eq("默认上下文 minus", 0, ctx[0]);
    TestAssert.eq("默认上下文 plus", 1, ctx[1]);

    TestAssert.eq("apply 单行替换", "a\nNEW\nc",
                      SuggestionBuilder.apply("a\nb\nc", 2, 2, "NEW"));
    TestAssert.eq("apply 多行替换", "a\nNEW\nc",
                      SuggestionBuilder.apply("a\nb\nb2\nc", 2, 3, "NEW"));
    TestAssert.eq("apply 越界原样返回", "a\nb", SuggestionBuilder.apply("a\nb", 5, 6, "X"));
    TestAssert.eq("apply 非法区间原样返回", "a\nb", SuggestionBuilder.apply("a\nb", 2, 1, "X"));

    int[] range = SuggestionBuilder.lineRange(10, 2, 3);
    TestAssert.eq("lineRange start", 8, range[0]);
    TestAssert.eq("lineRange end", 13, range[1]);
  }

  private static JsonObject obj(String json) {
    return JsonParser.parseString(json).getAsJsonObject();
  }
}
