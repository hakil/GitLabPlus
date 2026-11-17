package test;

import org.junit.jupiter.api.Test;
import com.hakil.gitlabplus.model.JobLogStyle;

/**
 * Job 日志着色分类自测。
 */
class JobLogStyleTest extends SuiteTestSupport {

    @Test
  void suite() {
    TestAssert.eq("[ERROR] 行", JobLogStyle.ERROR,
                      JobLogStyle.classify("[ERROR] Failed to execute goal"));
    TestAssert.eq("小写 error", JobLogStyle.ERROR,
                      JobLogStyle.classify("error: something bad"));
    TestAssert.eq("BUILD FAILURE", JobLogStyle.ERROR,
                      JobLogStyle.classify("[INFO] BUILD FAILURE"));
    TestAssert.eq("异常堆栈", JobLogStyle.ERROR,
                      JobLogStyle.classify("Caused by: java.io.IOException"));

    TestAssert.eq("WARNING", JobLogStyle.WARN,
                      JobLogStyle.classify("WARNING: deprecated API"));
    TestAssert.eq("WARN 前缀", JobLogStyle.WARN,
                      JobLogStyle.classify("WARN resolver-status"));

    TestAssert.eq("BUILD SUCCESS", JobLogStyle.SUCCESS,
                      JobLogStyle.classify("[INFO] BUILD SUCCESS"));
    TestAssert.eq("PASSED", JobLogStyle.SUCCESS,
                      JobLogStyle.classify("Tests run: 5, PASSED"));

    TestAssert.eq("section_start", JobLogStyle.SECTION,
                      JobLogStyle.classify("section_start:1699999999:build"));
    TestAssert.eq("section_end 带 ANSI", JobLogStyle.SECTION,
                      JobLogStyle.classify("section_end:1699999999:build\u001b[0K"));

    TestAssert.eq("命令回显", JobLogStyle.COMMAND,
                      JobLogStyle.classify("$ mvn clean install"));
    TestAssert.eq("runner 头", JobLogStyle.COMMAND,
                      JobLogStyle.classify("Running with gitlab-runner 17.0.0"));
    TestAssert.eq("Executing 脚本", JobLogStyle.COMMAND,
                      JobLogStyle.classify("Executing \"step_script\" stage of the job script"));

    TestAssert.eq("普通输出", JobLogStyle.NORMAL,
                      JobLogStyle.classify("Downloading from nexus: https://x/y.jar"));
    TestAssert.eq("空行", JobLogStyle.NORMAL, JobLogStyle.classify(""));
    TestAssert.eq("null", JobLogStyle.NORMAL, JobLogStyle.classify(null));

    // 优先级：错误优先于成功
    TestAssert.eq("错误优先于成功", JobLogStyle.ERROR,
                      JobLogStyle.classify("BUILD SUCCESS then ERROR? no it failed"));
    // 优先级：警告优先于成功
    TestAssert.eq("警告优先于成功", JobLogStyle.WARN,
                      JobLogStyle.classify("WARNING but SUCCESS keyword present"));
  }
}
