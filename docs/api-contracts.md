# GitLab API 契约

本文件记录插件已实现的端点、参数载体与取值规则。

## 1. Merge request

| 用途 | 端点 | 载体 |
| --- | --- | --- |
| 列表 | GraphQL `project.mergeRequests` | variables |
| 详情 | GraphQL `project.mergeRequest` | variables |
| 创建 | `POST /projects/:path/merge_requests` | JSON body |
| 关闭 / 重开 | `PUT /projects/:path/merge_requests/:iid` | JSON body，`state_event=close\|reopen` |
| 合并 | GraphQL `mergeRequestAccept` | variables |
| 草稿 | GraphQL `mergeRequestSetDraft` | variables |
| 审阅人 | GraphQL `mergeRequestSetReviewers` | variables，`reviewerUsernames[]` |
| 变基 | GraphQL `mergeRequestRebase` | variables |
| 批准 / 撤回 | `POST .../merge_requests/:iid/approve`、`unapprove` | 无参数 |
| 批准状态 | `GET .../merge_requests/:iid/approvals` | — |
| 变更 | `GET .../merge_requests/:iid/changes` | — |
| 版本 | `GET .../merge_requests/:iid/versions` | — |
| 原始文件 | `GET /projects/:path/repository/files/:file/raw?ref=:sha` | — |
| 项目成员 | `GET /projects/:path/members/all` | — |

规则：

- 列表与详情查询不包含 `diffRefs`。`DiffRefs.headSha` 为非空字段，MR 无可用 diff 时服务端返回 null 使整个响应失败；合并所需的 head sha 由 `GET .../changes` 获取。
- 查询字段集由启动时的 introspection 结果决定。`MergeRequest` 与 `MergeRequestPermissions` 的字段可用性按服务器版本裁剪，结果按 `serverUrl + username` 缓存；introspection 不可用时使用最小字段集。
- 关闭 / 重开使用 REST `state_event`，不使用 GraphQL `mergeRequestUpdate(state:)`。
- `project: null` 的三种成因（token 无效、项目不存在、无权限）无法由响应区分，需依次请求 `/api/v4/user` 与 `/api/v4/projects/:path` 判定。
- 同一源分支存在 open MR 时创建返回 `409`，响应消息包含既有 MR 编号。
- `/changes` 的 `diff_refs` 可能缺少 `start_sha`；缺失时以 `/versions` 中最近一条完整的三元 sha 补齐。

字段取值：`GET .../approvals` 的 `user_has_approved` 表示当前账号是否已批准；`MergeRequestPermissions.canApprove` 表示当前账号是否**仍可**批准，已批准后为 false。

## 2. 评审

| 用途 | 端点 | 载体 |
| --- | --- | --- |
| 列出讨论 | `GET .../merge_requests/:iid/discussions` | — |
| 新建评论 | `POST .../merge_requests/:iid/discussions` | JSON body，字段 `body` |
| 回复 | `POST .../discussions/:did/notes` | JSON body，字段 `body` |
| 解决 / 取消 | `PUT .../discussions/:did` | query，`resolved=true\|false` |
| 加 reaction | `POST .../notes/:nid/award_emoji` | query，`name=thumbsup` |
| 删 reaction | `DELETE .../notes/:nid/award_emoji/:awardId` | — |
| 查 reaction | `GET .../notes/:nid/award_emoji` | — |

规则：

- 参数载体按端点区分：discussions 与 notes 使用 JSON body（字段 `body`）；`resolved` 与 `award_emoji.name` 使用 query。
- `GET .../discussions` 的 `note` 对象不含 `award_emoji`。判断当前账号是否已对该评论点赞需单独请求 `GET .../notes/:nid/award_emoji`。
- `system=true` 的 note 不接受 reaction。
- 当前账号未批准时 `POST .../unapprove` 返回 `404`；调用前先以 `user_has_approved` 判断。

## 3. 行内评论 position

```
POST /projects/:path/merge_requests/:iid/discussions
     ?body=<comment>
     &position[base_sha]=...
     &position[start_sha]=...
     &position[head_sha]=...
     &position[position_type]=text
     &position[new_path]=...
     &position[old_path]=...
     &position[new_line]=<N>
     &position[old_line]=<M>
```

规则：

- 新增行：仅 `position[new_line]`
- 删除行：仅 `position[old_line]`
- 上下文行：两者均传
- 三个 sha 中任一缺失时不发起请求

suggestion 通过 note body 传递，无独立端点：

````
```suggestion:-N+M
替换内容
```
````

`-N` 为向上覆盖行数，`+M` 为向下覆盖行数。

## 4. 流水线与 Job

| 用途 | 端点 |
| --- | --- |
| MR 关联流水线 | `GET .../merge_requests/:iid/pipelines` |
| 按 sha 查 | `GET /projects/:path/pipelines?sha=<sha>` |
| 按 ref 查 | `GET /projects/:path/pipelines?ref=<branch>` |
| 单条流水线 | `GET /projects/:path/pipelines/:id` |
| 重跑 / 取消流水线 | `POST .../pipelines/:id/retry`、`cancel` |
| Job 列表 | `GET .../pipelines/:id/jobs` |
| 运行 / 重跑 / 取消 Job | `POST .../jobs/:id/play`、`retry`、`cancel` |
| Job 日志 | `GET .../jobs/:id/trace`，`text/plain` |

规则：

- `GET .../merge_requests/:iid/pipelines` 仅返回源分支触发的流水线。合并后在目标分支运行的流水线需通过 `?sha=<merge_commit_sha>`（squash 合并时为 `squash_commit_sha`）或 `?ref=<target_branch>` 查询；结果按 id 去重，倒序，最多 30 条。
- GraphQL 的 `Pipeline.id` 为全局 ID，REST 路径使用数值 id。
- 状态分类：
  - 进行中：`created`、`waiting_for_resource`、`preparing`、`pending`、`running`、`scheduled`
  - 已结束：`success`、`skipped`、`failed`、`canceled`、`cancelled`
  - 需人工：`manual`、`blocked`
- Job 能力判定：`play` 仅 `manual`；`retry` 为 `failed`、`canceled`、`success`；`cancel` 为 `running`、`pending`。

## 5. 认证

- 请求头 `PRIVATE-TOKEN`。账号密码对 `/api/v4/*` 与 `/api/graphql` 均返回 `401`。
- Token 读写前执行 `trim()`。
- 内置 GitLab 插件的凭据：账户元数据位于 `options\gitlab.xml`（`@State(name="GitLabAccounts")`），凭据键为 `CredentialAttributes("GitLab", accountId)`，值为包含 `accessToken` 的 JSON 或裸 token；读取通过平台 `PasswordSafe` 完成。
