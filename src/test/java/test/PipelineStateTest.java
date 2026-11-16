package test;

import org.junit.jupiter.api.Test;
import com.caidailian.gitlabplus.model.PipelineState;

/**
 * 流水线状态分类自测。
 *
 * <p>重点：{@code manual} / {@code blocked} 属于「不会自己结束但不能当作执行中」，
 * 必须命中 shouldStopWatching，否则关注会白等到超时。</p>
 */
class PipelineStateTest extends SuiteTestSupport {

    @Test
  void suite() {
    for (String running : new String[]{"created", "waiting_for_resource", "preparing", "pending", "running", "scheduled"}) {
      TestAssert.eq("RUNNING: " + running, PipelineState.Bucket.RUNNING, PipelineState.classify(running));
      TestAssert.eq("继续关注: " + running, false, PipelineState.shouldStopWatching(running));
    }

    TestAssert.eq("success", PipelineState.Bucket.SUCCESS, PipelineState.classify("success"));
    TestAssert.eq("failed", PipelineState.Bucket.FAILURE, PipelineState.classify("failed"));
    TestAssert.eq("canceled", PipelineState.Bucket.FAILURE, PipelineState.classify("canceled"));
    TestAssert.eq("cancelled 变体", PipelineState.Bucket.FAILURE, PipelineState.classify("cancelled"));
    TestAssert.eq("skipped 视为成功", PipelineState.Bucket.SUCCESS, PipelineState.classify("skipped"));

    TestAssert.eq("manual 需人工", PipelineState.Bucket.NEEDS_MANUAL, PipelineState.classify("manual"));
    TestAssert.eq("blocked 需人工", PipelineState.Bucket.NEEDS_MANUAL, PipelineState.classify("blocked"));
    TestAssert.eq("manual 停止关注", true, PipelineState.shouldStopWatching("manual"));
    TestAssert.eq("blocked 停止关注", true, PipelineState.shouldStopWatching("blocked"));

    TestAssert.eq("未知状态归类", PipelineState.Bucket.UNKNOWN, PipelineState.classify("whatever"));
    TestAssert.eq("空状态归类", PipelineState.Bucket.UNKNOWN, PipelineState.classify(""));
    TestAssert.eq("null 状态归类", PipelineState.Bucket.UNKNOWN, PipelineState.classify(null));
    TestAssert.eq("未知状态停止关注", true, PipelineState.shouldStopWatching("whatever"));

    TestAssert.eq("大小写不敏感", PipelineState.Bucket.RUNNING, PipelineState.classify("RUNNING"));
    TestAssert.eq("首尾空白", PipelineState.Bucket.SUCCESS, PipelineState.classify(" success "));

    TestAssert.eq("isSuccess(success)", true, PipelineState.isSuccess("success"));
    TestAssert.eq("isSuccess(skipped)", true, PipelineState.isSuccess("skipped"));
    TestAssert.eq("isSuccess(failed)", false, PipelineState.isSuccess("failed"));
    TestAssert.eq("isRunning(pending)", true, PipelineState.isRunning("pending"));
    TestAssert.eq("isRunning(success)", false, PipelineState.isRunning("success"));

    TestAssert.eq("中文名 running", "执行中", PipelineState.displayName("running"));
    TestAssert.eq("中文名 manual", "等待手动触发", PipelineState.displayName("manual"));
    TestAssert.eq("中文名未知", "未知", PipelineState.displayName(""));
    TestAssert.eq("失败结束文案", true,
                      PipelineState.watchConclusion("failed").contains("失败结束"));
    TestAssert.eq("需人工文案", true,
                      PipelineState.watchConclusion("manual").contains("等待人工操作"));
  }
}
