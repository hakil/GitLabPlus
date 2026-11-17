package test;

import org.junit.jupiter.api.Test;
import com.hakil.gitlabplus.model.FontZoom;

/**
 * 日志字号缩放规则自测。
 */
class FontZoomTest extends SuiteTestSupport {

    @Test
  void suite() {
    TestAssert.eq("默认 12", 12, FontZoom.DEFAULT_SIZE);
    TestAssert.eq("放大一次", 13, FontZoom.step(12, 1));
    TestAssert.eq("缩小一次", 11, FontZoom.step(12, -1));
    TestAssert.eq("上限封顶", FontZoom.MAX_SIZE, FontZoom.step(FontZoom.MAX_SIZE, 1));
    TestAssert.eq("下限封底", FontZoom.MIN_SIZE, FontZoom.step(FontZoom.MIN_SIZE, -1));
    TestAssert.eq("超上限归一", FontZoom.MAX_SIZE, FontZoom.step(99, 0));
    TestAssert.eq("超下限归一", FontZoom.MIN_SIZE, FontZoom.step(1, 0));
    TestAssert.eq("区间内不变", 14, FontZoom.step(14, 0));
    TestAssert.eq("可放大", true, FontZoom.canEnlarge(14));
    TestAssert.eq("上限不可放大", false, FontZoom.canEnlarge(FontZoom.MAX_SIZE));
    TestAssert.eq("可缩小", true, FontZoom.canShrink(14));
    TestAssert.eq("下限不可缩小", false, FontZoom.canShrink(FontZoom.MIN_SIZE));
  }
}
