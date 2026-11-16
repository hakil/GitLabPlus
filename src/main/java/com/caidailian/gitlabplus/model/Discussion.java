package com.caidailian.gitlabplus.model;

import com.caidailian.gitlabplus.api.JsonSupport;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * REST 讨论模型（{@code GET /merge_requests/:iid/discussions} 的元素）。
 *
 * <p>resolvable / resolved 在 GitLab REST 中位于 <b>note</b> 层，
 * 讨论级状态由首个可解决 note 推导。</p>
 */
public final class Discussion {

  public final String id;
  public final boolean individualNote;
  public final List<Note> notes;

  private Discussion(JsonObject node) {
    this.id = JsonSupport.string(node, "id", "");
    this.individualNote = JsonSupport.bool(node, "individual_note", false);
    List<Note> parsed = new ArrayList<>();
    JsonArray array = JsonSupport.arr(node, "notes");
    if (array != null) {
      for (JsonElement element : array) {
        if (element != null && element.isJsonObject()) {
          parsed.add(Note.from(element.getAsJsonObject()));
        }
      }
    }
    this.notes = List.copyOf(parsed);
  }

  public static Discussion from(JsonObject node) {
    return new Discussion(node);
  }

  public boolean resolvable() {
    for (Note note : notes) {
      if (note.resolvable) {
        return true;
      }
    }
    return false;
  }

  /**
   * 是否只是系统动态（例如 “added 1 commit”、“changed the description”）。
   *
   * <p>判据：所有 note 都是 {@code system=true}，或正文为空。
   * UI 默认过滤这类条目 —— 它们不是人写的评论，排在列表里会淹没真实讨论。</p>
   */
  public boolean isActivity() {
    if (notes.isEmpty()) {
      return true;
    }
    for (Note note : notes) {
      if (!note.system && note.body != null && !note.body.isBlank()) {
        return false;
      }
    }
    return true;
  }

  public boolean resolved() {
    for (Note note : notes) {
      if (note.resolvable) {
        return note.resolved;
      }
    }
    return false;
  }

  public String firstAuthor() {
    return notes.isEmpty() ? "" : notes.get(0).author;
  }

  public String firstBody() {
    return notes.isEmpty() ? "" : notes.get(0).body;
  }

  /** 单条评论（note）。 */
  public static final class Note {
    public final String id;
    public final String author;
    public final String authorName;
    public final String body;
    public final boolean system;
    public final String createdAt;
    public final boolean resolvable;
    public final boolean resolved;
    public final List<Award> awards;

    private Note(JsonObject node) {
      this.id = JsonSupport.string(node, "id", "");
      JsonObject authorObject = JsonSupport.obj(node, "author");
      this.author = JsonSupport.string(authorObject, "username", "");
      this.authorName = JsonSupport.string(authorObject, "name", this.author);
      this.body = JsonSupport.string(node, "body", "");
      this.system = JsonSupport.bool(node, "system", false);
      this.createdAt = JsonSupport.string(node, "created_at", "");
      this.resolvable = JsonSupport.bool(node, "resolvable", false);
      this.resolved = JsonSupport.bool(node, "resolved", false);
      List<Award> parsedAwards = new ArrayList<>();
      JsonArray emoji = JsonSupport.arr(node, "award_emoji");
      if (emoji != null) {
        for (JsonElement element : emoji) {
          if (element != null && element.isJsonObject()) {
            JsonObject awardNode = element.getAsJsonObject();
            String name = JsonSupport.string(awardNode, "name", "");
            if (!name.isBlank()) {
              parsedAwards.add(new Award(
                  JsonSupport.string(awardNode, "id", ""),
                  name,
                  JsonSupport.string(JsonSupport.obj(awardNode, "user"), "username", "")));
            }
          }
        }
      }
      this.awards = List.copyOf(parsedAwards);
    }

    public static Note from(JsonObject node) {
      return new Note(node);
    }

    /** 单条 reaction（id 用于取消点赞，username 用于判断是否自己点过）。 */
    public record Award(String id, String name, String username) {
    }

    /** reaction 计数（如 thumbsup 3）。 */
    public String awardSummary() {
      if (awards.isEmpty()) {
        return "";
      }
      java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
      for (Award award : awards) {
        counts.merge(award.name(), 1, Integer::sum);
      }
      StringBuilder sb = new StringBuilder();
      for (var entry : counts.entrySet()) {
        if (!sb.isEmpty()) {
          sb.append(' ');
        }
        sb.append(entry.getKey()).append(' ').append(entry.getValue());
      }
      return sb.toString();
    }
  }
}
