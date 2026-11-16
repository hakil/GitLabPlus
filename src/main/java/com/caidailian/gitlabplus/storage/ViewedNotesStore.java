package com.caidailian.gitlabplus.storage;

import com.caidailian.gitlabplus.model.ViewedIndex;
import com.intellij.ide.util.PropertiesComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * viewed（已读）标记的本地持久化。
 *
 * <p>语义与官方插件一致：以 <code>headSha</code> 为准 —— 同一个文件在
 * 新的 headSha 下重新视为未读（新提交可能改变 diff）。</p>
 *
 * <p>容量控制：每条记录带写入时间（值格式 {@code sha|millis}），
 * 配合 {@link ViewedIndex} 索引，超过 {@link ViewedIndex#MAX_ENTRIES} 条时淘汰最旧的，
 * 避免 PropertiesComponent 无限增长；同时 {@link #clearStale} 会在 headSha 变化后
 * 主动删除已失效的记录。</p>
 */
public final class ViewedNotesStore {

  private static final String KEY_PREFIX = "gitlabplus.viewed.";
  private static final String INDEX_KEY = "gitlabplus.viewed.index";
  private static final String SHA_SEPARATOR = "|";

  private ViewedNotesStore() {
  }

  public static boolean isViewed(String projectPath, String iid, String filePath, String headSha) {
    String stored = PropertiesComponent.getInstance().getValue(key(projectPath, iid, filePath));
    return isValidSha(stored, headSha);
  }

  public static void setViewed(String projectPath, String iid, String filePath, String headSha, boolean viewed) {
    String key = key(projectPath, iid, filePath);
    PropertiesComponent properties = PropertiesComponent.getInstance();
    List<ViewedIndex.Entry> index = ViewedIndex.parse(properties.getValue(INDEX_KEY));
    if (viewed) {
      long now = System.currentTimeMillis();
      properties.setValue(key, (headSha == null ? "" : headSha) + SHA_SEPARATOR + now);
      index = ViewedIndex.with(index, key, now);
      // 超量淘汰：先算要删的 key，再落地
      List<String> evicted = ViewedIndex.keysToEvict(index, ViewedIndex.MAX_ENTRIES);
      for (String oldKey : evicted) {
        properties.unsetValue(oldKey);
      }
      if (!evicted.isEmpty()) {
        index = ViewedIndex.parse(ViewedIndex.serialize(ViewedIndex.pruneTo(index, ViewedIndex.MAX_ENTRIES)));
      }
      properties.setValue(INDEX_KEY, ViewedIndex.serialize(index));
    }
    else {
      properties.unsetValue(key);
      properties.setValue(INDEX_KEY, ViewedIndex.serialize(ViewedIndex.without(index, key)));
    }
  }

  /**
   * 清理某个 MR 下已失效（headSha 已变化）的已读记录。
   *
   * <p>由 DiffPanel 在加载变更完成后调用一次：这样新提交把文件变回未读时，
   * 旧记录会被真正删除，而不是永久堆在配置里。</p>
   */
  public static void clearStale(String projectPath, String iid, @Nullable String headSha,
                                List<String> filePaths) {
    if (filePaths == null || filePaths.isEmpty()) {
      return;
    }
    String currentSha = headSha == null ? "" : headSha;
    PropertiesComponent properties = PropertiesComponent.getInstance();
    List<ViewedIndex.Entry> index = ViewedIndex.parse(properties.getValue(INDEX_KEY));
    boolean changed = false;
    for (String filePath : filePaths) {
      String key = key(projectPath, iid, filePath);
      String stored = properties.getValue(key);
      if (stored == null) {
        continue;
      }
      if (!currentSha.isBlank() && !currentSha.equals(parseSha(stored))) {
        properties.unsetValue(key);
        index = ViewedIndex.without(index, key);
        changed = true;
      }
    }
    if (changed) {
      properties.setValue(INDEX_KEY, ViewedIndex.serialize(index));
    }
  }

  /** 标记是否对当前 headSha 有效（纯函数，便于单测）。 */
  public static boolean isValidSha(@Nullable String storedValue, @Nullable String currentSha) {
    String storedSha = parseSha(storedValue);
    if (storedSha == null || storedSha.isBlank()) {
      return false;
    }
    if (currentSha == null || currentSha.isBlank()) {
      return true;
    }
    return storedSha.equals(currentSha);
  }

  /** 从存储值里取出 sha（兼容旧格式：纯 sha、无时间戳）。 */
  static @Nullable String parseSha(@Nullable String storedValue) {
    if (storedValue == null) {
      return null;
    }
    int index = storedValue.lastIndexOf(SHA_SEPARATOR);
    if (index < 0) {
      return storedValue;
    }
    return storedValue.substring(0, index);
  }

  static String key(String projectPath, String iid, String filePath) {
    // 不用 String.hashCode 做分区：不同 (project, iid) 的哈希可能相同，
    // 会读到另一个 MR 的已读记录（低概率但无往不在，且极难排查）。
    // 代价：旧版本写入的、带哈希的 key 不再命中，升级后已读标记归零（可接受）。
    return KEY_PREFIX + sanitize(projectPath) + '.' + sanitize(iid) + '.' + sanitize(filePath);
  }

  private static String sanitize(String filePath) {
    if (filePath == null) {
      return "unknown";
    }
    return filePath.replace('/', '_').replace('\\', '_');
  }
}
