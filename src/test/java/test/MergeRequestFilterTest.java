package test;

import org.junit.jupiter.api.Test;
import com.hakil.gitlabplus.model.MergeRequestFilter;
import org.junit.jupiter.api.Test;
import com.hakil.gitlabplus.model.MergeRequestInfo;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import java.util.List;

/**
 * 列表能力（P1）纯函数自测：搜索过滤、分页去重、state 映射。
 */
class MergeRequestFilterTest extends SuiteTestSupport {

    @Test
  void suite() {
    MergeRequestInfo login = mr("1", "修复登录", "feature/login", "dev");
    MergeRequestInfo docs = mr("2", "补充文档", "docs/readme", "dev");
    MergeRequestInfo loginOpt = mr("3", "登录流程优化", "feature/login-opt", "main");
    List<MergeRequestInfo> rows = List.of(login, docs, loginOpt);

    TestAssert.eq("空搜索返回全部", 3, MergeRequestFilter.filter(rows, "").size());
    TestAssert.eq("空搜索返回全部(blank)", 3, MergeRequestFilter.filter(rows, "   ").size());
    TestAssert.eq("空搜索返回全部(null)", 3, MergeRequestFilter.filter(rows, null).size());
    TestAssert.eq("按标题过滤", 2, MergeRequestFilter.filter(rows, "登录").size());
    TestAssert.eq("按分支过滤", 2, MergeRequestFilter.filter(rows, "feature").size());
    TestAssert.eq("按 iid 过滤", 1, MergeRequestFilter.filter(rows, "2").size());
    TestAssert.eq("大小写不敏感", 2, MergeRequestFilter.filter(rows, "FEATURE").size());
    TestAssert.eq("无匹配", 0, MergeRequestFilter.filter(rows, "zzz").size());

    List<MergeRequestInfo> merged = MergeRequestFilter.mergeDistinct(
        List.of(login, docs), List.of(docs, loginOpt));
    TestAssert.eq("分页去重后条数", 3, merged.size());
    TestAssert.eq("分页去重保序", "1", merged.get(0).iid);
    TestAssert.eq("分页去重尾部", "3", merged.get(2).iid);

    List<MergeRequestInfo> again = MergeRequestFilter.mergeDistinct(merged, List.of(login));
    TestAssert.eq("重复追加不增长", 3, again.size());

    TestAssert.eq("state:all -> null", null, MergeRequestFilter.toGraphQLState("all"));
    TestAssert.eq("state:空 -> null", null, MergeRequestFilter.toGraphQLState(""));
    TestAssert.eq("state:opened", "opened", MergeRequestFilter.toGraphQLState("opened"));
    TestAssert.eq("state:大写归一", "merged", MergeRequestFilter.toGraphQLState("MERGED"));
  }

  private static MergeRequestInfo mr(String iid, String title, String source, String target) {
    JsonObject node = JsonParser.parseString("{\"iid\":\"" + iid + "\",\"title\":\"" + title
                                                 + "\",\"sourceBranch\":\"" + source
                                                 + "\",\"targetBranch\":\"" + target
                                                 + "\",\"state\":\"OPENED\",\"mergeable\":true}").getAsJsonObject();
    return MergeRequestInfo.fromList(node);
  }
}
