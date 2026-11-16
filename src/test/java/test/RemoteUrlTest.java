package test;

import org.junit.jupiter.api.Test;
import com.caidailian.gitlabplus.git.GitProjectResolver;

/**
 * GitProjectResolver.parseRemoteUrl 的纯函数自测（不依赖 IDE 运行时）。
 */
class RemoteUrlTest extends SuiteTestSupport {

    @Test
  void suite() {
    String[] cases = {
        "git@gitlab.com:group/sub/project.git",
        "https://gitlab.example.com/group/project.git",
        "https://gitlab.example.com:8443/group/sub/project",
        "ssh://git@gitlab.example.com:2222/group/project.git",
        "git://gitlab.example.com/group/project.git",
        "http://10.0.0.5/group/project.git",
        "file:///d/repo",
        "C:/local/repo",
        "/home/user/repo",
        "../relative",
        "",
        "not a url"
    };
    for (String c : cases) {
      GitProjectResolver.Resolved r = GitProjectResolver.parseRemoteUrl(c);
      String rendered = r == null ? "null" : (r.serverUrl() + " | " + r.projectPath());
      String expectation = expected(c);
      boolean ok = expectation.equals(rendered);
      if (!ok) {
        TestAssert.eq("parse " + c, expectation, rendered);
      }
      System.out.printf("%-58s -> %-52s %s%n", c, rendered, ok ? "OK" : "EXPECTED " + expectation);
    }
  }

  private static String expected(String url) {
    return switch (url) {
      case "git@gitlab.com:group/sub/project.git" -> "https://gitlab.com | group/sub/project";
      case "https://gitlab.example.com/group/project.git" -> "https://gitlab.example.com | group/project";
      case "https://gitlab.example.com:8443/group/sub/project" -> "https://gitlab.example.com:8443 | group/sub/project";
      case "ssh://git@gitlab.example.com:2222/group/project.git" -> "https://gitlab.example.com | group/project";
      case "git://gitlab.example.com/group/project.git" -> "https://gitlab.example.com | group/project";
      case "http://10.0.0.5/group/project.git" -> "http://10.0.0.5 | group/project";
      default -> "null";
    };
  }
}
