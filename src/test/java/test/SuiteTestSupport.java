package test;

import org.junit.jupiter.api.AfterEach;

/**
 * 测试基类：在每个测试方法结束后上报 {@link TestAssert} 收集到的失败。
 */
abstract class SuiteTestSupport {

  @AfterEach
  void flushAssertions() {
    TestAssert.flush();
  }
}
