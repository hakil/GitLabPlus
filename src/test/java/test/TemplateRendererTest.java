package test;

import org.junit.jupiter.api.Test;
import com.caidailian.gitlabplus.model.TemplateRenderer;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import java.util.Map;

/**
 * 标题/描述模板渲染自测。
 */
class TemplateRendererTest extends SuiteTestSupport {

    @Test
  void suite() {
    Map<String, String> vars = new LinkedHashMap<>();
    vars.put("source", "feature/login-page");
    vars.put("target", "dev");
    vars.put("branchTitle", "login page");
    vars.put("project", "javaers/caidailian-loan");

    TestAssert.eq("替换单个占位符", "login page",
                      TemplateRenderer.render("${branchTitle}", vars));
    TestAssert.eq("多占位符", "feature/login-page → dev",
                      TemplateRenderer.render("${source} → ${target}", vars));
    TestAssert.eq("未知占位符保留", "x ${unknown} y",
                      TemplateRenderer.render("x ${unknown} y", vars));
    TestAssert.eq("重复占位符全部替换", "dev/dev",
                      TemplateRenderer.render("${target}/${target}", vars));
    TestAssert.eq("null 模板为空串", "", TemplateRenderer.render(null, vars));
    TestAssert.eq("null 变量为空串", "[]",
                      TemplateRenderer.render("[${missing}]", Map.of("missing", "")));

    Map<String, String> nullVar = new LinkedHashMap<>();
    nullVar.put("source", null);
    TestAssert.eq("null 值替换为空", "", TemplateRenderer.render("${source}", nullVar));

    TestAssert.eq("branchTitle 去前缀+空格", "login page",
                      TemplateRenderer.branchTitle("feature/login-page"));
    TestAssert.eq("branchTitle 下划线", "fix timeout",
                      TemplateRenderer.branchTitle("bugfix/fix_timeout"));
    TestAssert.eq("branchTitle 无前缀", "master",
                      TemplateRenderer.branchTitle("master"));
    TestAssert.eq("branchTitle 空", "", TemplateRenderer.branchTitle(""));
    TestAssert.eq("branchTitle null", "", TemplateRenderer.branchTitle(null));

    String history = TemplateRenderer.render(TemplateRenderer.DEFAULT_TITLE_TEMPLATE, vars);
    TestAssert.eq("默认标题为合并格式", "Merge branch 'feature/login-page' into 'dev'", history);

    String description = TemplateRenderer.render(TemplateRenderer.DEFAULT_DESCRIPTION_TEMPLATE, vars);
    TestAssert.eq("默认描述为空（与官方一致）", "", description);

    String customDescription = TemplateRenderer.render(
        "## 变更说明\n- ${branchTitle}\n分支：`${source}` → `${target}`", vars);
    TestAssert.eq("自定义描述可渲染", true, customDescription.contains("- login page"));
    TestAssert.eq("自定义描述含分支", true, customDescription.contains("`feature/login-page`"));
    TestAssert.eq("自定义描述无未替换占位符", false, customDescription.contains("${"));
  }
}
