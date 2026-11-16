package com.caidailian.gitlabplus.api;

/**
 * GraphQL 契约。
 *
 * <p>字段集刻意保持最小：只取列表展示与合并动作必需字段，避免引用
 * <code>enterprise</code>、<code>mergeStatusEnum</code> 等对 GitLab 版本有要求的字段。
 * 合并 mutation 的入参形状参照内置 GitLab 插件
 * <code>graphql/query/mergeRequestAccept.graphql</code>（sha 必填，squash 走同一 mutation）。</p>
 */
public final class GraphQLQueries {

  private GraphQLQueries() {
  }

  /** 列出项目的 merge request（默认取 opened）。 */
  public static final String LIST_MERGE_REQUESTS = """
      query($projectId: ID!, $state: MergeRequestState, $pageSize: Int = 50, $cursor: String) {
        project(fullPath: $projectId) {
          mergeRequests(state: $state, after: $cursor, first: $pageSize) {
            nodes {
              iid
              title
              sourceBranch
              targetBranch
              state
              webUrl
              draft
              mergeable
            }
            pageInfo { hasNextPage endCursor }
          }
        }
      }
      """;

  /** 合并前读取单个 MR 的合并相关状态（sha、默认提交信息、squash 能力、权限）。 */
  public static final String MERGE_REQUEST_DETAILS = """
      query($projectId: ID!, $iid: String!) {
        project(fullPath: $projectId) {
          mergeRequest(iid: $iid) {
            iid
            title
            state
            webUrl
            mergeable
            conflicts
            draft
            shouldRemoveSourceBranch
            forceRemoveSourceBranch
            squashOnMerge
            squashReadOnly
            defaultMergeCommitMessage
            defaultSquashCommitMessage
            userPermissions { merge }
          }
        }
      }
      """;

  /** 合并 / squash 并合并（squash=false 时忽略 squashCommitMessage）。 */
  public static final String MERGE_REQUEST_ACCEPT = """
      mutation($projectId: ID!, $mergeRequestId: String!, $commitMessage: String,
               $squashCommitMessage: String, $sha: String!, $withSquash: Boolean,
               $shouldRemoveSourceBranch: Boolean) {
        mergeRequestAccept(input: {
          projectPath: $projectId,
          iid: $mergeRequestId,
          commitMessage: $commitMessage,
          squashCommitMessage: $squashCommitMessage,
          sha: $sha,
          squash: $withSquash,
          shouldRemoveSourceBranch: $shouldRemoveSourceBranch
        }) {
          mergeRequest {
            iid
            state
            webUrl
            title
          }
          errors
        }
      }
      """;

  /** 合并后轮询确认状态（GraphQL 合并是异步落地的）。 */
  public static final String GET_MERGE_REQUEST_STATE = """
      query($projectId: ID!, $iid: String!) {
        project(fullPath: $projectId) {
          mergeRequest(iid: $iid) {
            iid
            state
            mergeCommitSha
          }
        }
      }
      """;

  /** 详情（P1）：基本信息 + 审批/审阅人/标签/权限/pipeline，覆盖 CE 可用字段。 */
  public static final String MERGE_REQUEST_FULL_DETAILS = """
      query($projectId: ID!, $iid: String!) {
        project(fullPath: $projectId) {
          mergeRequest(iid: $iid) {
            iid
            title
            description
            state
            webUrl
            draft
            mergeable
            conflicts
            createdAt
            updatedAt
            sourceBranch
            targetBranch
            shouldRemoveSourceBranch
            forceRemoveSourceBranch
            squashOnMerge
            squashReadOnly
            defaultMergeCommitMessage
            defaultSquashCommitMessage
            author { username name }
            assignees(first: 100) { nodes { username name } }
            reviewers(first: 100) { nodes { username name } }
            labels(first: 100) { nodes { title color } }
            approvedBy(first: 100) { nodes { username name } }
            userPermissions { canApprove canMerge createNote updateMergeRequest }
            headPipeline { status }
          }
        }
      }
      """;

  /** 关闭 / 重新打开（state: CLOSE | REOPEN）。 */
  public static final String MERGE_REQUEST_UPDATE_STATE = """
      mutation($projectId: ID!, $mergeRequestId: String!, $state: MergeRequestNewState) {
        mergeRequestUpdate(input: {
          projectPath: $projectId,
          iid: $mergeRequestId,
          state: $state
        }) {
          mergeRequest { iid state }
        }
      }
      """;

  /** Draft / Ready 切换。 */
  public static final String MERGE_REQUEST_SET_DRAFT = """
      mutation($projectId: ID!, $mergeRequestId: String!, $isDraft: Boolean!) {
        mergeRequestSetDraft(input: {
          projectPath: $projectId,
          iid: $mergeRequestId,
          draft: $isDraft
        }) {
          mergeRequest { iid draft }
          errors
        }
      }
      """;

  /** 设置审阅人（按用户名列表整体覆盖）。 */
  public static final String MERGE_REQUEST_SET_REVIEWERS = """
      mutation($projectId: ID!, $mergeRequestId: String!, $reviewerUsernames: [String!]!) {
        mergeRequestSetReviewers(input: {
          projectPath: $projectId,
          iid: $mergeRequestId,
          reviewerUsernames: $reviewerUsernames
        }) {
          mergeRequest { iid }
          errors
        }
      }
      """;

  /** Rebase 源分支。 */
  public static final String MERGE_REQUEST_REBASE = """
      mutation($projectId: ID!, $mergeRequestId: String!) {
        mergeRequestRebase(input: {
          projectPath: $projectId,
          iid: $mergeRequestId
        }) {
          errors
        }
      }
      """;

}
