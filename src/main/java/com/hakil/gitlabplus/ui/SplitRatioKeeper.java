package com.hakil.gitlabplus.ui;

import org.jetbrains.annotations.Nullable;

import javax.swing.JSplitPane;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.function.BooleanSupplier;

/**
 * 让 {@link JSplitPane} 的分隔条在容器尺寸变化时**按比例**跟随。
 *
 * <p>为什么需要它：{@code setResizeWeight} 只决定“新增空间怎么分”，
 * 而 {@code setDividerLocation(double)} 只按**当时**的尺寸换算成一次像素值。
 * 于是工具窗口被压矮（典型场景：在 IDEA 底部打开 Git / 运行面板）后，
 * 详情区还被顶在原来的像素位置，最终被挤成 0 高度、完全看不见。</p>
 *
 * <p>这里在 resize 时重新按比例计算分隔位置，并留一个容差：
 * 用户手动拖过分隔条时不会被强行拉回。</p>
 */
public final class SplitRatioKeeper {

  private SplitRatioKeeper() {
  }

  public static void keep(JSplitPane split, double ratio) {
    keep(split, ratio, 40, () -> true);
  }

  public static void keep(JSplitPane split, double ratio, int tolerance) {
    keep(split, ratio, tolerance, () -> true);
  }

  /**
   * @param enabled 纠正的前置条件（例如「详情区可见时才纠正」）
   */
  public static void keep(JSplitPane split, double ratio, int tolerance, BooleanSupplier enabled) {
    if (split == null) {
      return;
    }
    split.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent event) {
        if (enabled != null && !enabled.getAsBoolean()) {
          return;
        }
        apply(split, ratio, tolerance);
      }
    });
  }

  /** 立即按比例应用一次（不满足容差时不动）。 */
  public static void apply(@Nullable JSplitPane split, double ratio, int tolerance) {
    if (split == null) {
      return;
    }
    int extent = split.getOrientation() == JSplitPane.VERTICAL_SPLIT
        ? split.getHeight() : split.getWidth();
    if (extent <= 0) {
      return;
    }
    int min = split.getMinimumDividerLocation();
    int max = split.getMaximumDividerLocation();
    if (max <= min) {
      return;
    }
    int desired = Math.max(min, Math.min((int) Math.round(extent * ratio), max));
    if (Math.abs(split.getDividerLocation() - desired) > tolerance) {
      split.setDividerLocation(desired);
    }
  }
}
