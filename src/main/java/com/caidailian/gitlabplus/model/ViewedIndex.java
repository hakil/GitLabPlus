package com.caidailian.gitlabplus.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 「已读」标记的索引：记录每个标记的写入时间，用于容量淘汰。
 *
 * <p>存储层（PropertiesComponent）单值保存一个列表，格式
 * {@code key|millis;key|millis}。key 由 projectPath|iid|filePath 派生，
 * 因此不能按 MR 前缀枚举，需要这份索引才能做「最旧优先淘汰」。</p>
 */
public final class ViewedIndex {

  /** 索引里最多保留的条目数（超出按时间最旧淘汰）。 */
  public static final int MAX_ENTRIES = 500;

  private static final String ENTRY_SEPARATOR = ";";
  private static final String FIELD_SEPARATOR = "|";

  private ViewedIndex() {
  }

  public record Entry(String key, long millis) {
  }

  public static String serialize(List<Entry> entries) {
    if (entries == null || entries.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (Entry entry : entries) {
      if (entry == null || entry.key() == null || entry.key().isBlank()) {
        continue;
      }
      if (!sb.isEmpty()) {
        sb.append(ENTRY_SEPARATOR);
      }
      sb.append(entry.key()).append(FIELD_SEPARATOR).append(entry.millis());
    }
    return sb.toString();
  }

  public static List<Entry> parse(String raw) {
    List<Entry> entries = new ArrayList<>();
    if (raw == null || raw.isBlank()) {
      return entries;
    }
    for (String part : raw.split(ENTRY_SEPARATOR)) {
      if (part == null || part.isBlank()) {
        continue;
      }
      int index = part.lastIndexOf(FIELD_SEPARATOR);
      if (index <= 0 || index == part.length() - 1) {
        continue;
      }
      String key = part.substring(0, index).trim();
      String millisText = part.substring(index + 1).trim();
      if (key.isEmpty()) {
        continue;
      }
      long millis;
      try {
        millis = Long.parseLong(millisText);
      }
      catch (NumberFormatException e) {
        continue;
      }
      entries.add(new Entry(key, millis));
    }
    return entries;
  }

  /** 加入/更新一个条目（同 key 去重，时间取最新）。 */
  public static List<Entry> with(List<Entry> entries, String key, long millis) {
    List<Entry> result = without(entries, key);
    if (key != null && !key.isBlank()) {
      result.add(new Entry(key, millis));
    }
    return result;
  }

  public static List<Entry> without(List<Entry> entries, String key) {
    List<Entry> result = new ArrayList<>();
    if (entries != null) {
      for (Entry entry : entries) {
        if (entry != null && !entry.key().equals(key)) {
          result.add(entry);
        }
      }
    }
    return result;
  }

  /**
   * 容量淘汰：超过 {@code maxEntries} 时保留最新的若干条。
   *
   * @return 保留的条目（时间倒序）；未超限时原样返回
   */
  public static List<Entry> pruneTo(List<Entry> entries, int maxEntries) {
    if (entries == null) {
      return List.of();
    }
    if (maxEntries <= 0) {
      return List.of();
    }
    if (entries.size() <= maxEntries) {
      return new ArrayList<>(entries);
    }
    List<Entry> sorted = new ArrayList<>(entries);
    sorted.sort(Comparator.comparingLong(Entry::millis).reversed());
    return new ArrayList<>(sorted.subList(0, maxEntries));
  }

  /** 需要被删除的 key（超出容量时最旧的那些）。 */
  public static List<String> keysToEvict(List<Entry> entries, int maxEntries) {
    List<Entry> kept = pruneTo(entries, maxEntries);
    List<Entry> all = entries == null ? List.of() : entries;
    if (kept.size() >= all.size()) {
      return List.of();
    }
    List<String> keptKeys = new ArrayList<>();
    for (Entry entry : kept) {
      keptKeys.add(entry.key());
    }
    List<String> evicted = new ArrayList<>();
    for (Entry entry : all) {
      if (!keptKeys.contains(entry.key()) && !evicted.contains(entry.key())) {
        evicted.add(entry.key());
      }
    }
    return evicted;
  }
}
