package test;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * 资源束完整性自测：英文与中文键集合必须一致，且没有空文案。
 *
 * <p>漏配的键在界面上会显示成 {@code detail.action.merge} 这类原文，
 * 属于「不报错但明显不对」的缺陷，所以用断言锁住。</p>
 */
class BundleTest extends SuiteTestSupport {

  private static final String BASE = "/messages/GitLabPlusBundle";

    @Test
  void suite() {
    Properties en = load(BASE + ".properties");
    Properties zh = load(BASE + "_zh_CN.properties");

    TestAssert.eq("英文键数量 > 230", true, en.size() > 230);
    TestAssert.eq("中文键数量 == 英文", en.size(), zh.size());

    Set<String> onlyEn = new TreeSet<>(en.stringPropertyNames());
    onlyEn.removeAll(zh.stringPropertyNames());
    TestAssert.eq("中文缺键", Set.of(), onlyEn);

    Set<String> onlyZh = new TreeSet<>(zh.stringPropertyNames());
    onlyZh.removeAll(en.stringPropertyNames());
    TestAssert.eq("中文多余键", Set.of(), onlyZh);

    Set<String> emptyValues = new LinkedHashSet<>();
    for (String key : en.stringPropertyNames()) {
      if (en.getProperty(key, "").isBlank()) {
        emptyValues.add(key);
      }
      if (zh.getProperty(key, "").isBlank()) {
        emptyValues.add(key);
      }
    }
    TestAssert.eq("无空文案", Set.of(), emptyValues);

    // 抽查关键术语：中文侧应使用中文动作词（避免再次混排）
    TestAssert.eq("合并按钮为中文", "合并", zh.getProperty("detail.action.merge"));
    TestAssert.eq("批准按钮为中文", "批准", zh.getProperty("detail.action.approve"));
    TestAssert.eq("解决按钮为中文", "解决", zh.getProperty("review.resolve"));
    TestAssert.eq("英文侧为英文", "Merge", en.getProperty("detail.action.merge"));

    // 关键回归：显式请求英文时必须命中英文，不能回退到 JVM 默认（中文）
    TestAssert.eq("Locale.ENGLISH 命中英文", "Merge",
                      com.hakil.gitlabplus.i18n.GitLabPlusBundle.messageFor(
                          java.util.Locale.ENGLISH, "detail.action.merge"));
    TestAssert.eq("Locale.ENGLISH 带参数", "Merge !217",
                      com.hakil.gitlabplus.i18n.GitLabPlusBundle.messageFor(
                          java.util.Locale.ENGLISH, "merge.titleMerge", "217"));
    TestAssert.eq("Locale.SIMPLIFIED_CHINESE 命中中文", "合并",
                      com.hakil.gitlabplus.i18n.GitLabPlusBundle.messageFor(
                          java.util.Locale.SIMPLIFIED_CHINESE, "detail.action.merge"));
    TestAssert.eq("缺键不影响", "no.such.key",
                      com.hakil.gitlabplus.i18n.GitLabPlusBundle.messageFor(
                          java.util.Locale.ENGLISH, "no.such.key"));
    // 技术名词保留：英文侧与中文侧都应含 MR / CI 这类词
    TestAssert.eq("中文保留 MR", true, zh.getProperty("list.newMr").contains("MR"));
  }

  private static Properties load(String resource) {
    Properties properties = new Properties();
    try (InputStream in = BundleTest.class.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("resource not found: " + resource);
      }
      properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
    catch (Exception e) {
      throw new IllegalStateException("cannot load " + resource, e);
    }
    return properties;
  }
}
