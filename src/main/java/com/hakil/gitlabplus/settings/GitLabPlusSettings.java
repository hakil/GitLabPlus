package com.hakil.gitlabplus.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 插件持久化配置：GitLab 服务器地址与项目全路径。
 *
 * <p>Access Token 不落在本配置里，改存 IDEA 凭据库（见 {@link GitLabTokenStore}），
 * 避免明文写入 <code>gitlab-plus.xml</code>。</p>
 */
@State(name = "GitLabPlusSettings", storages = @Storage("gitlab-plus.xml"))
public final class GitLabPlusSettings implements PersistentStateComponent<GitLabPlusSettings.State> {

  private State state = new State();

  public static GitLabPlusSettings getInstance() {
    return ApplicationManager.getApplication().getService(GitLabPlusSettings.class);
  }

  @Override
  public @NotNull State getState() {
    return state;
  }

  @Override
  public void loadState(@NotNull State state) {
    this.state = state;
  }

  public String getServerUrl() {
    return state.serverUrl;
  }

  public void setServerUrl(String serverUrl) {
    state.serverUrl = serverUrl;
  }

  public String getProjectPath() {
    return state.projectPath;
  }

  public void setProjectPath(String projectPath) {
    state.projectPath = projectPath;
  }

  public String getToken() {
    return GitLabTokenStore.getToken(state.serverUrl);
  }
  public void setToken(@Nullable String token) {
    GitLabTokenStore.setToken(state.serverUrl, token);
  }

  /** 合并成功后是否自动切到目标分支（默认关）。 */
  public boolean isCheckoutTargetAfterMerge() {
    return state.checkoutTargetAfterMerge;
  }

  public void setCheckoutTargetAfterMerge(boolean value) {
    state.checkoutTargetAfterMerge = value;
  }

  /** 合并成功后是否删除已合并的本地源分支（默认关）。 */
  public boolean isDeleteLocalSourceAfterMerge() {
    return state.deleteLocalSourceAfterMerge;
  }

  public void setDeleteLocalSourceAfterMerge(boolean value) {
    state.deleteLocalSourceAfterMerge = value;
  }

  /** 原始标题模板（可能为 null/空 = 使用内置默认）。 */
  public String getTitleTemplateRaw() {
    return state.titleTemplate;
  }

  /** 原始描述模板（可能为 null/空 = 使用内置默认）。 */
  public String getDescriptionTemplateRaw() {
    return state.descriptionTemplate;
  }

  /** 新建 MR 的标题模板（支持 ${source}/${target}/${branchTitle}/${project}）。 */
  public String getTitleTemplate() {
    return state.titleTemplate == null || state.titleTemplate.isBlank()
        ? com.hakil.gitlabplus.model.TemplateRenderer.DEFAULT_TITLE_TEMPLATE
        : state.titleTemplate;
  }

  public void setTitleTemplate(String value) {
    state.titleTemplate = value;
  }

  /** 新建 MR 的描述模板。 */
  public String getDescriptionTemplate() {
    return state.descriptionTemplate == null
        ? com.hakil.gitlabplus.model.TemplateRenderer.DEFAULT_DESCRIPTION_TEMPLATE
        : state.descriptionTemplate;
  }

  public void setDescriptionTemplate(String value) {
    state.descriptionTemplate = value;
  }

  /** MR 列表默认状态：opened / merged / closed / all。 */
  public String getDefaultMrState() {
    return state.defaultMrState == null || state.defaultMrState.isBlank() ? "opened" : state.defaultMrState;
  }

  public void setDefaultMrState(String value) {
    state.defaultMrState = value;
  }

  /** 新建 MR 时「合并后删除源分支」默认是否勾选。 */
  public boolean isDefaultRemoveSourceBranch() {
    return state.defaultRemoveSourceBranch;
  }

  public void setDefaultRemoveSourceBranch(boolean value) {
    state.defaultRemoveSourceBranch = value;
  }

  /** 新建 MR 时「Squash commits」默认是否勾选。 */
  public boolean isDefaultSquashOnMerge() {
    return state.defaultSquashOnMerge;
  }

  public void setDefaultSquashOnMerge(boolean value) {
    state.defaultSquashOnMerge = value;
  }

  /**
   * 工具窗口「项目」下拉中显示的项目（有序，顺序即下拉顺序）。
   *
   * <p>空表示尚未配置 → 显示全部解析到的远程项目（首次使用开箱即用）。</p>
   */
  public java.util.List<String> getDropdownProjects() {
    return state.dropdownProjects == null
        ? java.util.List.of() : java.util.List.copyOf(state.dropdownProjects);
  }

  public void setDropdownProjects(java.util.List<String> projectPaths) {
    state.dropdownProjects = projectPaths == null
        ? new java.util.ArrayList<>() : new java.util.ArrayList<>(projectPaths);
  }

  /** 流水线关注超时（分钟，默认 30）。 */
  public int getPipelineWatchTimeoutMinutes() {
    return state.pipelineWatchTimeoutMinutes <= 0 ? 30 : state.pipelineWatchTimeoutMinutes;
  }

  public void setPipelineWatchTimeoutMinutes(int minutes) {
    state.pipelineWatchTimeoutMinutes = minutes;
  }

  /**
   * 持久化状态，字段名即 XML 元素名，改动需保持向后兼容。
   */
  public static final class State {
    public String serverUrl = "https://gitlab.com";
    public String projectPath = "";
    public boolean checkoutTargetAfterMerge = false;
    public boolean deleteLocalSourceAfterMerge = false;
    public String titleTemplate = null;
    public String descriptionTemplate = null;
    public int pipelineWatchTimeoutMinutes = 30;
    public String defaultMrState = "opened";
    public boolean defaultRemoveSourceBranch = true;
    public boolean defaultSquashOnMerge = false;
    public java.util.List<String> dropdownProjects = new java.util.ArrayList<>();
  }
}
