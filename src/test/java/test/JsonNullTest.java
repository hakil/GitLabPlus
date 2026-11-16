package test;

import org.junit.jupiter.api.Test;
import com.caidailian.gitlabplus.api.JsonSupport;
import org.junit.jupiter.api.Test;
import com.caidailian.gitlabplus.model.MergeRequestInfo;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;

/**
 * GraphQL 响应中 null 节点的回归测试。
 *
 * <p>真实缺陷背景：Gson 的 {@code getAsJsonObject(name)} 只做强制转换，
 * 当 GraphQL 返回 {@code {"project": null}}（项目不存在 / token 无权限）时抛
 * {@code ClassCastException: JsonNull cannot be cast to JsonObject}。
 * 本测试锁定「所有访问必须返回 null 而不是抛异常」的契约。</p>
 */
class JsonNullTest extends SuiteTestSupport {

    @Test
  void suite() {
    // 1. project: null
    JsonObject data = obj("{\"project\":null}");
    TestAssert.eq("obj(project) is null", JsonSupport.obj(data, "project") == null);

    // 2. mergeRequests: null
    JsonObject project = obj("{\"mergeRequests\":null}");
    TestAssert.eq("obj(mergeRequests) is null", JsonSupport.obj(project, "mergeRequests") == null);

    // 3. mergeRequest: null + errors: null
    JsonObject payload = obj("{\"mergeRequestAccept\":{\"mergeRequest\":null,\"errors\":null}}");
    JsonObject accept = JsonSupport.obj(payload, "mergeRequestAccept");
    TestAssert.eq("obj(mergeRequest) is null", JsonSupport.obj(accept, "mergeRequest") == null);
    TestAssert.eq("arr(errors) is null", JsonSupport.arr(accept, "errors") == null);
    TestAssert.eq("errorMessages(null) is null", JsonSupport.errorMessages(null) == null);

    // 4. errors 数组含 null 元素
    JsonArray errors = (JsonArray) JsonParser.parseString("[null,{\"message\":\"boom\"}]");
    TestAssert.eq("errorMessages skips null element", "boom".equals(JsonSupport.errorMessages(errors)));

    // 5. MR 节点中 diffRefs / userPermissions / title 为 null
    JsonObject node = obj("{\"iid\":\"7\",\"title\":null,\"diffRefs\":null,\"userPermissions\":null,"
                              + "\"state\":null,\"mergeable\":null}");
    MergeRequestInfo info = MergeRequestInfo.fromDetails(node);
    TestAssert.eq("iid parsed", "7".equals(info.iid));
    TestAssert.eq("null title -> empty", "".equals(info.title));
    TestAssert.eq("null diffRefs -> empty sha", "".equals(info.headSha));
    TestAssert.eq("null userPermissions -> false", !info.userCanMerge);
    TestAssert.eq("null state -> empty", "".equals(info.state));

    // 6. 正常节点仍然解析正确
    JsonObject full = obj("{\"iid\":\"9\",\"title\":\"T\",\"state\":\"OPENED\",\"mergeable\":true,"
                              + "\"diffRefs\":{\"headSha\":\"abc\"},\"userPermissions\":{\"merge\":true}}");
    MergeRequestInfo ok = MergeRequestInfo.fromDetails(full);
    TestAssert.eq("headSha parsed", "abc".equals(ok.headSha));
    TestAssert.eq("userCanMerge parsed", ok.userCanMerge);
    TestAssert.eq("state parsed", "OPENED".equals(ok.state));
  }

  private static JsonObject obj(String json) {
    return JsonParser.parseString(json).getAsJsonObject();
  }

  private static int check(String name, boolean condition) {
    System.out.printf("%-42s %s%n", name, condition ? "OK" : "FAIL");
    return condition ? 0 : 1;
  }
}
