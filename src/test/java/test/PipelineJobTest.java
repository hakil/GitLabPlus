package test;

import org.junit.jupiter.api.Test;
import com.hakil.gitlabplus.model.PipelineJob;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;

/**
 * job 状态判定自测（手动触发的判定依据）。
 */
class PipelineJobTest extends SuiteTestSupport {

    @Test
  void suite() {
    PipelineJob manual = job("{\"id\":101,\"name\":\"deploy:prod\",\"stage\":\"deploy\","
                                 + "\"status\":\"manual\",\"duration\":null,\"web_url\":\"u\"}");
    TestAssert.eq("manual 判定", true, manual.isManual());
    TestAssert.eq("manual 非 running", false, manual.isRunning());
    TestAssert.eq("manual 非 failed", false, manual.failed());
    TestAssert.eq("id 解析", "101", manual.id());
    TestAssert.eq("stage 解析", "deploy", manual.stage());
    TestAssert.eq("duration null -> 显示 —", "—", manual.durationLabel());

    PipelineJob running = job("{\"id\":102,\"name\":\"build\",\"status\":\"running\",\"duration\":12.6}");
    TestAssert.eq("running 判定", true, running.isRunning());
    TestAssert.eq("running 非 manual", false, running.isManual());
    TestAssert.eq("duration 取整显示", "13s", running.durationLabel());

    PipelineJob pending = job("{\"id\":103,\"name\":\"test\",\"status\":\"pending\"}");
    TestAssert.eq("pending 视为进行中", true, pending.isRunning());

    PipelineJob failed = job("{\"id\":104,\"name\":\"lint\",\"status\":\"failed\",\"duration\":78}");
    TestAssert.eq("failed 判定", true, failed.failed());
    TestAssert.eq("duration 分钟显示", "1m18s", failed.durationLabel());

    PipelineJob manualUpper = job("{\"id\":105,\"status\":\"MANUAL\"}");
    TestAssert.eq("MANUAL 大小写不敏感", true, manualUpper.isManual());

    PipelineJob noDuration = job("{\"id\":106,\"status\":\"success\"}");
    TestAssert.eq("缺 duration 字段", "—", noDuration.durationLabel());

    // 操作可用性：修正「手动触发后失败就无法重跑」
    TestAssert.eq("manual 可运行", true, manual.canPlay());
    TestAssert.eq("manual 不可重跑", false, manual.canRetry());
    TestAssert.eq("manual 不可取消", false, manual.canCancel());

    TestAssert.eq("failed 可重跑", true, failed.canRetry());
    TestAssert.eq("failed 不可运行", false, failed.canPlay());
    TestAssert.eq("failed 不可取消", false, failed.canCancel());

    PipelineJob canceled = job("{\"id\":107,\"status\":\"canceled\"}");
    TestAssert.eq("canceled 可重跑", true, canceled.canRetry());

    PipelineJob success = job("{\"id\":108,\"status\":\"success\"}");
    TestAssert.eq("success 可重跑", true, success.canRetry());

    TestAssert.eq("running 可取消", true, running.canCancel());
    TestAssert.eq("running 不可重跑", false, running.canRetry());
    TestAssert.eq("pending 可取消", true, pending.canCancel());

    PipelineJob blank = job("{\"id\":109,\"status\":\"\"}");
    TestAssert.eq("空状态均不可操作", false,
                      blank.canPlay() || blank.canRetry() || blank.canCancel());

    TestAssert.eq("数组内 null 元素返回 null", null,
                      PipelineJob.fromNullable(com.google.gson.JsonNull.INSTANCE));
  }

  private static PipelineJob job(String json) {
    return PipelineJob.from(JsonParser.parseString(json).getAsJsonObject());
  }
}
