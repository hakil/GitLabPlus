package test;

import org.junit.jupiter.api.Test;
import com.hakil.gitlabplus.model.JobLog;

/**
 * Job 日志尾部截断自测。
 */
class JobLogTest extends SuiteTestSupport {

    @Test
  void suite() {
    TestAssert.eq("null → 空", "", JobLog.tail(null, 10));
    TestAssert.eq("空串 → 空", "", JobLog.tail("", 10));
    TestAssert.eq("未超限原样返回", "a\nb\nc", JobLog.tail("a\nb\nc", 10));
    TestAssert.eq("刚好等于限制", "a\nb", JobLog.tail("a\nb", 2));
    TestAssert.eq("超限取尾部行数", true, JobLog.tail("a\nb\nc\nd", 2).contains("c"));
    TestAssert.eq("超限包含最后一行", true, JobLog.tail("a\nb\nc\nd", 2).endsWith("d"));
    TestAssert.eq("超限不包含第一行", false,
                      JobLog.tail("a\nb\nc\nd", 2).replaceAll("(?s)^.*省略.*\\n", "").contains("\na\n"));
    TestAssert.eq("超限带省略说明", true,
                      JobLog.tail("a\nb\nc\nd", 2).startsWith("…（已省略前面 2 行"));
    TestAssert.eq("限制为 0 → 空", "", JobLog.tail("a\nb", 0));
    TestAssert.eq("Windows 换行也按行切", true, JobLog.tail("a\r\nb\r\nc", 2).contains("b"));

    TestAssert.eq("行数 null", 0, JobLog.lineCount(null));
    TestAssert.eq("行数 3", 3, JobLog.lineCount("a\nb\nc"));
    TestAssert.eq("行数 CRLF 3", 3, JobLog.lineCount("a\r\nb\r\nc"));
  }
}
