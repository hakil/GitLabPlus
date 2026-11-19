package test;

import com.hakil.gitlabplus.git.GitRefresher;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

/**
 * 合并后 fetch 的仓库定位。
 *
 * <p>多仓库工程里要同步刷新的仓库必须与本次合并对应；定位失败时不能随便挑一个，
 * 否则会把无关仓库放到前台阻塞。</p>
 */
class GitRefresherTest extends SuiteTestSupport {

  @Test
  void indexOfRootMatchesPathForms() {
    List<String> roots = List.of(
        "D:\\projects\\a",
        "D:/projects/b",
        "D:\\projects\\c\\");

    TestAssert.eq("Windows 反斜杠路径", 0, GitRefresher.indexOfRoot(roots, new File("D:\\projects\\a")));
    TestAssert.eq("正斜杠路径", 1, GitRefresher.indexOfRoot(roots, new File("D:/projects/b")));
    TestAssert.eq("末尾带分隔符", 2, GitRefresher.indexOfRoot(roots, new File("D:/projects/c")));
    TestAssert.eq("不在列表中返回 -1", -1,
                  GitRefresher.indexOfRoot(roots, new File("D:/projects/zzz")));
    TestAssert.eq("roots 为空返回 -1", -1, GitRefresher.indexOfRoot(List.of(), new File("D:/x")));
    TestAssert.eq("workDir 为 null 返回 -1", -1, GitRefresher.indexOfRoot(roots, null));
    TestAssert.eq("roots 为 null 返回 -1", -1, GitRefresher.indexOfRoot(null, new File("D:/x")));
    TestAssert.eq("列表含 null 元素不抛", -1,
                  GitRefresher.indexOfRoot(java.util.Arrays.asList(null, null), new File("D:/x")));
  }

  @Test
  void normalizeCollapsesSeparators() {
    TestAssert.eq("反斜杠转正斜杠", "D:/projects/a", GitRefresher.normalize("D:\\projects\\a"));
    TestAssert.eq("去掉尾部分隔符", "D:/projects/a", GitRefresher.normalize("D:/projects/a/"));
    TestAssert.eq("多个尾部分隔符", "D:/projects/a", GitRefresher.normalize("D:\\projects\\a\\\\"));
    TestAssert.eq("null 返回空串", "", GitRefresher.normalize(null));
    TestAssert.eq("根路径保留分隔符", "/", GitRefresher.normalize("/"));
  }
}
