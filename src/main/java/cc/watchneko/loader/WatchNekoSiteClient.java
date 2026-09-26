package cc.watchneko.loader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Logger;

/**
 * 访问 WatchNekoWebSite 的版本查询接口。
 *
 * <p>只用到 {@code /api/latest}，它返回当前 {@code latest_version} 指针指向的版本。
 * 站点<b>不提供下载接口</b>，产物由静态站直接服务，地址规则是
 * {@code {站点地址}/versions/{fileName}}，所以这里顺带把下载地址拼好交给调用方。
 */
public final class WatchNekoSiteClient {

    private final String site;
    private final Logger logger;
    private final HttpClient http = HttpClient.newHttpClient();

    public WatchNekoSiteClient(String site, Logger logger) {
        this.site = site;
        this.logger = logger;
    }

    /**
     * 查询最新版本。
     *
     * @throws Exception 站点不可达、返回非 200，或者响应里缺少必要字段
     */
    public VersionInfo fetchLatest() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(site + "/api/latest"))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("版本接口返回 HTTP " + response.statusCode());
        }

        JsonObject latest = JsonParser.parseString(response.body()).getAsJsonObject();

        String version = latest.get("version").getAsString();
        String fileName = latest.get("fileName").getAsString();

        // 历史数据没有 fileHash，取不到就留 null，下载完会跳过校验
        String fileHash = latest.has("fileHash") && !latest.get("fileHash").isJsonNull()
                ? latest.get("fileHash").getAsString()
                : null;

        logger.info("已获取到最新最热WatchNeko版本：" + version);
        return new VersionInfo(version, site + "/versions/" + fileName, fileHash);
    }
}
