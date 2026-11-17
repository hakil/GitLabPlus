package com.hakil.gitlabplus.model;

/**
 * 日志字号的缩放规则（Ctrl + 滚轮 / 重置按钮共用）。
 */
public final class FontZoom {

  public static final int MIN_SIZE = 8;
  public static final int MAX_SIZE = 28;
  public static final int DEFAULT_SIZE = 12;

  private FontZoom() {
  }

  /** 在合法区间内步进一次（delta 可为负；delta=0 用于归一化当前值）。 */
  public static int step(int current, int delta) {
    int next = current + delta;
    if (next < MIN_SIZE) {
      return MIN_SIZE;
    }
    return Math.min(next, MAX_SIZE);
  }

  public static boolean canEnlarge(int current) {
    return current < MAX_SIZE;
  }

  public static boolean canShrink(int current) {
    return current > MIN_SIZE;
  }
}
