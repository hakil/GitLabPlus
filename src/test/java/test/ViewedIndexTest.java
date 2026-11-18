package test;

import org.junit.jupiter.api.Test;
import com.hakil.gitlabplus.model.ViewedIndex;

import java.util.List;

/**
 * 已读索引进/出、容量淘汰自测。
 */
class ViewedIndexTest extends SuiteTestSupport {

    @Test
  void suite() {
    // 序列化 / 解析往返
    List<ViewedIndex.Entry> entries = List.of(
        new ViewedIndex.Entry("a.1", 100L),
        new ViewedIndex.Entry("b.2", 200L));
    String raw = ViewedIndex.serialize(entries);
    TestAssert.eq("序列化", "a.1|100;b.2|200", raw);
    TestAssert.eq("解析条数", 2, ViewedIndex.parse(raw).size());
    TestAssert.eq("解析 key", "a.1", ViewedIndex.parse(raw).get(0).key());
    TestAssert.eq("解析时间", 200L, ViewedIndex.parse(raw).get(1).millis());

    TestAssert.eq("空串解析", 0, ViewedIndex.parse("").size());
    TestAssert.eq("null 解析", 0, ViewedIndex.parse(null).size());
    TestAssert.eq("空序列化", "", ViewedIndex.serialize(List.of()));
    TestAssert.eq("脏数据跳过", 1, ViewedIndex.parse("good|1;;bad|;nodigit|abc").size());
    TestAssert.eq("脏 key 被忽略", 0, ViewedIndex.serialize(
        List.of(new ViewedIndex.Entry(" ", 1L))).length());

    // 去重
    List<ViewedIndex.Entry> deduped = ViewedIndex.with(entries, "a.1", 300L);
    TestAssert.eq("同 key 去重后条数", 2, deduped.size());
    TestAssert.eq("同 key 时间更新", 300L, deduped.get(deduped.size() - 1).millis());

    // 删除
    TestAssert.eq("without 条数", 1, ViewedIndex.without(entries, "a.1").size());
    TestAssert.eq("without 保留另一条", "b.2", ViewedIndex.without(entries, "a.1").get(0).key());

    // 容量淘汰：保留最新
    List<ViewedIndex.Entry> many = List.of(
        new ViewedIndex.Entry("old", 1L),
        new ViewedIndex.Entry("mid", 5L),
        new ViewedIndex.Entry("new", 9L));
    List<ViewedIndex.Entry> kept = ViewedIndex.pruneTo(many, 2);
    TestAssert.eq("淘汰后条数", 2, kept.size());
    TestAssert.eq("最旧被淘汰", false, containsKey(kept, "old"));
    TestAssert.eq("最新被保留", true, containsKey(kept, "new"));

    TestAssert.eq("未超限原样返回", 3, ViewedIndex.pruneTo(many, 10).size());
    TestAssert.eq("上限 0 清空", 0, ViewedIndex.pruneTo(many, 0).size());

    List<String> evicted = ViewedIndex.keysToEvict(many, 2);
    TestAssert.eq("待删 key", List.of("old"), evicted);
    TestAssert.eq("未超限无待删", List.of(), ViewedIndex.keysToEvict(many, 10));
    TestAssert.eq("null 无待删", List.of(), ViewedIndex.keysToEvict(null, 2));
  }

  private static boolean containsKey(List<ViewedIndex.Entry> entries, String key) {
    for (ViewedIndex.Entry entry : entries) {
      if (entry.key().equals(key)) {
        return true;
      }
    }
    return false;
  }
}
