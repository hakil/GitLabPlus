package test;

import org.junit.jupiter.api.Test;
import com.caidailian.gitlabplus.settings.OfficialGitLabTokenImporter;

/**
 * 内置插件凭据解析的回归测试。
 *
 * <p>内置插件把 {@code GitLabCredentials}（sealed class）经 kotlinx 序列化为 JSON 存入
 * PasswordSafe，旧格式则为裸 token。这里锁定两种形态的提取契约。</p>
 */
class TokenImportTest extends SuiteTestSupport {

    @Test
  void suite() {
    TestAssert.eq("kotlinx JSON",
                      "glpat-jN9xY2kLm4Qp",
                      OfficialGitLabTokenImporter.extractToken(
                          "{\"type\":\"org.jetbrains.plugins.gitlab.authentication.GitLabCredentials.Token\","
                              + "\"accessToken\":\"glpat-jN9xY2kLm4Qp\"}"));

    TestAssert.eq("kotlinx JSON 带空白", "glpat-abc",
                      OfficialGitLabTokenImporter.extractToken("  {\"accessToken\":\"glpat-abc\"}  "));

    TestAssert.eq("裸 token 原样返回", "glpat-raw-token",
                      OfficialGitLabTokenImporter.extractToken("  glpat-raw-token \n"));

    TestAssert.eq("JSON 但无 accessToken -> 原样", "{\"foo\":1}",
                      OfficialGitLabTokenImporter.extractToken("{\"foo\":1}"));

    TestAssert.eq("非 JSON 花括号开头但非法 -> 原样", "{not-json",
                      OfficialGitLabTokenImporter.extractToken("{not-json"));
  }

  private static int check(String name, String expected, String actual) {
    boolean ok = expected.equals(actual);
    System.out.printf("%-34s %s%s%n", name, ok ? "OK" : "FAIL", ok ? "" : "  expected=" + expected + " actual=" + actual);
    return ok ? 0 : 1;
  }
}
