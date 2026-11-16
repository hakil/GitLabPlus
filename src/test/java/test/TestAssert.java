package test;

import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.List;

/**
 * 断言收集器。
 *
 * <p>一个 suite 内的多条断言不立即失败，而是全部执行后统一抛出，
 * 便于一次看到所有不通过的条目。由 {@link SuiteTestSupport} 在每个测试方法结束时统一 flush。</p>
 */
final class TestAssert {

  private static final List<String> FAILURES = new ArrayList<>();

  private TestAssert() {
  }

  /** 相等断言（与原 check(name, expected, actual) 语义一致）。 */
  static void eq(String name, Object expected, Object actual) {
    boolean ok = expected == null ? actual == null : expected.equals(actual);
    if (!ok) {
      FAILURES.add(name + ": expected=" + expected + " actual=" + actual);
    }
  }

  /** 布尔断言（与原 check(name, boolean) 重载对应）。 */
  static void eq(String name, boolean condition) {
    if (!condition) {
      FAILURES.add(name + ": expected true");
    }
  }

  /** 真值断言。 */
  static void isTrue(String name, boolean condition) {
    if (!condition) {
      FAILURES.add(name + ": expected true");
    }
  }

  /** 统一上报本测试方法中累积的失败。 */
  static void flush() {
    if (FAILURES.isEmpty()) {
      return;
    }
    String message = String.join(System.lineSeparator(), FAILURES);
    FAILURES.clear();
    Assertions.fail(message);
  }
}
