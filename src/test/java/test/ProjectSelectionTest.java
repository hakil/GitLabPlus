package test;

import org.junit.jupiter.api.Test;
import com.hakil.gitlabplus.model.ProjectSelection;

import java.util.List;

/**
 * 项目下拉双列表的顺序/差异计算自测。
 */
class ProjectSelectionTest extends SuiteTestSupport {

  private static final List<String> AVAILABLE =
      List.of("group/a", "group/b", "group/c");

    @Test
  void suite() {
    // 未配置 → 全部（首次开箱即用）
    TestAssert.eq("未配置 = 全部", List.of("group/a", "group/b", "group/c"),
                      ProjectSelection.ordered(List.of(), AVAILABLE));
    TestAssert.eq("null 配置 = 全部", List.of("group/a", "group/b", "group/c"),
                      ProjectSelection.ordered(null, AVAILABLE));

    // 配置顺序优先
    TestAssert.eq("按配置顺序", List.of("group/c", "group/a"),
                      ProjectSelection.ordered(List.of("group/c", "group/a"), AVAILABLE));

    // 失效项静默忽略
    TestAssert.eq("忽略已失效项目", List.of("group/b"),
                      ProjectSelection.ordered(List.of("group/deleted", "group/b"), AVAILABLE));

    // 去重
    TestAssert.eq("配置内重复去重", List.of("group/a", "group/b"),
                      ProjectSelection.ordered(List.of("group/a", "group/a", "group/b"), AVAILABLE));

    // 配置里全部失效 → 空（不是回退到全部）
    TestAssert.eq("全部失效返回空", List.of(),
                      ProjectSelection.ordered(List.of("gone/x"), AVAILABLE));

    // 可用列表为空
    TestAssert.eq("可用为空", List.of(),
                      ProjectSelection.ordered(List.of("group/a"), List.of()));

    // newEntries：新增但未配置的项目（刷新时放入左侧）
    TestAssert.eq("new 检出", List.of("group/c"),
                      ProjectSelection.newEntries(List.of("group/a", "group/b"), AVAILABLE));
    TestAssert.eq("无 new", List.of(),
                      ProjectSelection.newEntries(AVAILABLE, AVAILABLE));
    TestAssert.eq("未配置时 new = 全部", AVAILABLE,
                      ProjectSelection.newEntries(List.of(), AVAILABLE));
  }
}
