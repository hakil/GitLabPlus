package test;

import org.junit.jupiter.api.Test;
import com.hakil.gitlabplus.model.DiffParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.util.List;

/**
 * P3 基础能力自测：unified diff 解析与行号映射。
 */
class DiffParserTest extends SuiteTestSupport {

    @Test
  void suite() {
    String diff = """
        @@ -1,4 +1,5 @@
         line1
        -old2
        +new2
        +added
         line3
         line4""";

    List<DiffParser.Hunk> hunks = DiffParser.parseUnifiedDiff(diff);
    TestAssert.eq("hunk 数量", 1, hunks.size());
    DiffParser.Hunk hunk = hunks.get(0);
    TestAssert.eq("oldStart", 1, hunk.oldStart());
    TestAssert.eq("oldCount", 4, hunk.oldCount());
    TestAssert.eq("newStart", 1, hunk.newStart());
    TestAssert.eq("newCount", 5, hunk.newCount());
    TestAssert.eq("行数", 6, hunk.lines().size());

    TestAssert.eq("上下文行号", 1, hunk.lines().get(0).newLine());
    TestAssert.eq("删除行 oldLine", 2, hunk.lines().get(1).oldLine());
    TestAssert.eq("删除行无 newLine", -1, hunk.lines().get(1).newLine());
    TestAssert.eq("新增行 newLine", 2, hunk.lines().get(2).newLine());
    TestAssert.eq("新增行无 oldLine", -1, hunk.lines().get(2).oldLine());

    TestAssert.eq("findNewLine 命中", "new2", textOf(DiffParser.findNewLine(hunks, 2)));
    TestAssert.eq("findNewLine 命中新增", "added", textOf(DiffParser.findNewLine(hunks, 3)));
    TestAssert.eq("findNewLine 未命中", "null", textOf(DiffParser.findNewLine(hunks, 99)));

    DiffParser.FileDiff file = DiffParser.fromChange(
        JsonParser.parseString("{\"old_path\":\"a.txt\",\"new_path\":\"a.txt\",\"diff\":"
                                   + JsonParser.parseString("null").toString()
                                   + "}").getAsJsonObject());
    TestAssert.eq("空 diff 不抛异常", 0, file.hunks().size());

    String twoHunks = """
        @@ -1,2 +1,2 @@
        -a
        +b
         c
        @@ -10,2 +10,3 @@
         x
        +y
         z""";
    List<DiffParser.Hunk> multi = DiffParser.parseUnifiedDiff(twoHunks);
    TestAssert.eq("多 hunk 数量", 2, multi.size());
    TestAssert.eq("第二 hunk oldStart", 10, multi.get(1).oldStart());
    TestAssert.eq("第二 hunk 新增行", 11, multi.get(1).lines().get(1).newLine());

    String noNewline = """
        @@ -1 +1 @@
        -a
        +b
        \\ No newline at end of file""";
    List<DiffParser.Hunk> trailing = DiffParser.parseUnifiedDiff(noNewline);
    TestAssert.eq("反斜线标记被识别", true,
                      trailing.get(0).lines().stream()
                          .anyMatch(l -> l.kind() == DiffParser.DiffLine.Kind.NO_NEWLINE));

    TestAssert.eq("二进制 diff 无 hunk", 0,
                      DiffParser.parseUnifiedDiff("Binary files a/x and b/x differ").size());

    JsonArray changes = JsonParser.parseString("""
        [
          {"old_path":"/dev/null","new_path":"src/New.java","new_file":true,"diff":"@@ -0,0 +1,2 @@\\n+a\\n+b"},
          {"old_path":"src/Old.java","new_path":"src/Old.java","deleted_file":true,"diff":"@@ -1,1 +0,0 @@\\n-x"},
          {"old_path":"src/A.java","new_path":"src/B.java","renamed_file":true,"diff":""}
        ]
        """).getAsJsonArray();
    List<DiffParser.FileDiff> files = DiffParser.parseChanges(changes);
    TestAssert.eq("changes 数量", 3, files.size());
    TestAssert.eq("新增文件路径", "src/New.java", files.get(0).displayPath());
    TestAssert.eq("新增行数", 2, files.get(0).addedLines());
    TestAssert.eq("删除文件路径", "src/Old.java", files.get(1).displayPath());
    TestAssert.eq("删除行数", 1, files.get(1).removedLines());
    TestAssert.eq("重命名展示", "src/A.java → src/B.java", files.get(2).displayPath());
  }

  private static String textOf(DiffParser.DiffLine line) {
    return line == null ? "null" : line.text();
  }
}
