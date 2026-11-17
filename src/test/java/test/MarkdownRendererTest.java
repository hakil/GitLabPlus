package test;

import org.junit.jupiter.api.Test;
import com.hakil.gitlabplus.model.MarkdownRenderer;

/**
 * Markdown → HTML 渲染自测（MR 描述展示）。
 */
class MarkdownRendererTest extends SuiteTestSupport {

    @Test
  void suite() {
    String h1 = MarkdownRenderer.toHtml("# 一级标题");
    TestAssert.eq("标题 h1", true, h1.contains("<h1>一级标题</h1>"));

    String h3 = MarkdownRenderer.toHtml("### 三级");
    TestAssert.eq("标题 h3", true, h3.contains("<h3>三级</h3>"));

    TestAssert.eq("不是标题（无空格）", false,
                      MarkdownRenderer.toHtml("#标签").contains("<h1>"));

    String bold = MarkdownRenderer.toHtml("这是 **粗体** 文本");
    TestAssert.eq("粗体", true, bold.contains("<b>粗体</b>"));

    String italic = MarkdownRenderer.toHtml("这是 *斜体* 文本");
    TestAssert.eq("斜体", true, italic.contains("<i>斜体</i>"));
    TestAssert.eq("粗体不被误判为斜体", false,
                      MarkdownRenderer.toHtml("**粗**").contains("<i>"));

    String code = MarkdownRenderer.toHtml("执行 `mvn clean` 命令");
    TestAssert.eq("行内代码", true, code.contains("<code>mvn clean</code>"));

    String ul = MarkdownRenderer.toHtml("- 第一项\n- 第二项");
    TestAssert.eq("无序列表", true, ul.contains("<ul><li>第一项</li><li>第二项</li></ul>"));

    String ol = MarkdownRenderer.toHtml("1. 第一步\n2. 第二步");
    TestAssert.eq("有序列表", true, ol.contains("<ol><li>第一步</li><li>第二步</li></ol>"));

    String quote = MarkdownRenderer.toHtml("> 注意事项");
    TestAssert.eq("引用", true, quote.contains("<blockquote>注意事项</blockquote>"));

    String link = MarkdownRenderer.toHtml("[文档](https://example.com/doc)");
    TestAssert.eq("链接", true, link.contains("<a href=\"https://example.com/doc\">文档</a>"));

    String fence = MarkdownRenderer.toHtml("```\nint a = 1; // *不是斜体*\n```");
    TestAssert.eq("代码块使用 pre", true, fence.contains("<pre"));
    TestAssert.eq("代码块内不做行内替换", false, fence.contains("<i>"));

    String xss = MarkdownRenderer.toHtml("<script>alert(1)</script>");
    TestAssert.eq("HTML 被转义", true, xss.contains("&lt;script&gt;"));
    TestAssert.eq("不产生真实 script 标签", false, xss.contains("<script>"));

    String quoted = MarkdownRenderer.toHtml("属性 \"引号\" 也转义");
    TestAssert.eq("引号转义", true, quoted.contains("&quot;"));

    TestAssert.eq("空输入不抛异常", true,
                      MarkdownRenderer.toHtml(null).contains("<html>"));
    TestAssert.eq("空串不抛异常", true,
                      MarkdownRenderer.toHtml("   ").contains("<html>"));

    String mixed = MarkdownRenderer.toHtml("""
        ## 变更说明
        - 修复 **登录** 超时
        - 影响 `AuthService`

        > 需要回归测试

        ```java
        authService.login();
        ```
        """);
    TestAssert.eq("混合语法-标题", true, mixed.contains("<h2>变更说明</h2>"));
    TestAssert.eq("混合语法-粗体", true, mixed.contains("<b>登录</b>"));
    TestAssert.eq("混合语法-行内代码", true, mixed.contains("<code>AuthService</code>"));
    TestAssert.eq("混合语法-代码块", true, mixed.contains("<pre"));
    TestAssert.eq("混合语法-标签闭合", true, mixed.contains("</ul>"));
    TestAssert.eq("混合语法-整体闭合", true, mixed.trim().endsWith("</body></html>"));
  }
}
