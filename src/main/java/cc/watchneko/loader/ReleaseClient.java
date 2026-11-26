/*
 * WatchNekoLoader — 服务端启动时自动拉取并加载最新版 WatchNeko 反作弊
 * Copyright (C) 2026 jiuxian1337 (P01_4rU5er)
 *
 * 本程序是自由软件：你可以遵照自由软件基金会发布的 GNU 通用公共许可证
 * （第 3 版）条款重新发布和/或修改它。
 *
 * 本程序的发布是希望它能有用，但不提供任何担保，甚至不提供可商售性或
 * 适用于特定用途的默示担保。详见 GNU 通用公共许可证。
 *
 * 你应该已经随本程序收到一份 GNU 通用公共许可证的副本。如果没有，
 * 见 <https://www.gnu.org/licenses/>。
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package cc.watchneko.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 GitHub Releases 查询最新版 WatchNeko。
 *
 * <p>首选 {@code api.github.com} 的 {@code /releases/latest}：一次请求就能拿到
 * tag、产物文件名和 SHA-256（{@code digest} 字段），实测也是所有路径里最快的。
 *
 * <p>API 不可用时退到网页端：Atom 订阅拿 tag，再读 {@code expanded_assets} 拿产物文件名。
 * 这两步都同时请求 github.com 和镜像站，谁先回用谁（页面只有几 KB，不值得等）。
 * 走这条路拿不到哈希，下载时会跳过校验。
 *
 * <p>为什么必须有兜底：{@code api.github.com} 未认证时按 IP 限流 60 次/小时，
 * 共用出口 IP 的多台服务器可能撞到 403。这种情况下仍然要能把反作弊装上。
 *
 * <p>注意产物名<b>不能</b>由版本号拼出——历史上出现过 tag 与文件名不一致的版本，
 * 详见 {@link ReleaseInfo#assetName()}。
 */
public final class ReleaseClient {

    /** GitHub 仓库。换仓库只改这两处。 */
    private static final String OWNER = "jiuxian1337";
    private static final String REPO = "WatchNeko";

    /** 镜像站，与官方内容一致，作为下载与查询的兜底。 */
    private static final String MIRROR = "https://watchneko.zkmjnic.tech";

    private static final String GITHUB = "https://github.com";

    /** api.github.com 不带 User-Agent 会直接 403。 */
    private static final String USER_AGENT = "jiuxian-baka-WatchNekoLoader/1.0";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration METADATA_TIMEOUT = Duration.ofSeconds(10);

    private static final Pattern ATOM_TAG = Pattern.compile("/releases/tag/([^\"]+)\"");
    private static final Pattern EXPANDED_ASSET = Pattern.compile("/releases/download/[^/\"]+/([^/\"]+\\.jar)");

    private final Logger logger;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final SourceRacer racer;

    public ReleaseClient(Logger logger) {
        this.logger = logger;
        this.racer = new SourceRacer(logger);
    }

    /**
     * 查询最新版。
     *
     * @throws Exception 所有查询途径都失败
     */
    public ReleaseInfo fetchLatest() throws Exception {
        try {
            ReleaseInfo info = fromApi();
            logger.info("已获取到最新最热WatchNeko版本：" + info.version());
            return info;
        } catch (ApiException e) {
            // 限流是可预期的：未认证请求按 IP 每小时 60 次，共用出口 IP 的服务器很容易撞上
            if (e.rateLimited()) {
                logger.info("GitHub API 限流（共用 IP 每小时 60 次），改用网页端查询");
            } else {
                logger.info("GitHub API 查询失败（" + e.getMessage() + "），改用网页端查询");
            }
        } catch (Exception e) {
            logger.info("GitHub API 查询失败（" + describe(e) + "），改用网页端查询");
        }

        String tag = fetchTag();
        String asset = fetchAssetName(tag);
        logger.info("已获取到最新最热WatchNeko版本：" + tag);
        return new ReleaseInfo(tag, asset, null, sourcesFor(tag, asset));
    }

    /** 走 api.github.com，信息最全，也是最快的一条路。 */
    private ReleaseInfo fromApi() throws Exception {
        String url = "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest";
        HttpResponse<String> response = http.send(
                get(url).header("Accept", "application/vnd.github+json").build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new ApiException(response.statusCode(),
                    "releases/latest 返回 HTTP " + response.statusCode());
        }
        return parseApiResponse(response.body());
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        return message != null ? message : error.getClass().getSimpleName();
    }

    /** API 返回了非 200，带状态码，好区分「限流」和别的故障。 */
    private static final class ApiException extends Exception {

        private final int status;

        ApiException(int status, String message) {
            super(message);
            this.status = status;
        }

        /** 403/429 就是限流了；GitHub 对它限流时给的就是 403。 */
        boolean rateLimited() {
            return status == 403 || status == 429;
        }
    }

    /**
     * 解析 {@code /releases/latest} 的响应。
     *
     * <p>单独拆出来是为了能拿真实响应做测试：这一段一旦解析错，插件就会拿错产物名去下载。
     */
    static ReleaseInfo parseApiResponse(String json) {
        JsonObject release = JsonParser.parseString(json).getAsJsonObject();
        String tag = release.get("tag_name").getAsString();

        JsonArray assets = release.getAsJsonArray("assets");
        if (assets == null || assets.isEmpty()) {
            throw new IllegalStateException("最新 release 没有产物");
        }

        JsonObject asset = chooseAsset(assets);
        String name = asset.get("name").getAsString();
        return new ReleaseInfo(tag, name, parseDigest(asset), sourcesFor(tag, name));
    }

    private static JsonObject chooseAsset(JsonArray assets) {
        JsonObject first = assets.get(0).getAsJsonObject();
        for (var element : assets) {
            JsonObject candidate = element.getAsJsonObject();
            String name = candidate.get("name").getAsString();
            if (name.contains("bukkit") && name.endsWith(".jar")) {
                return candidate;
            }
        }
        return first;
    }

    /** {@code digest} 形如 {@code sha256:6f4b...}，没有这个字段时返回 {@code null}。 */
    private static String parseDigest(JsonObject asset) {
        if (!asset.has("digest") || asset.get("digest").isJsonNull()) {
            return null;
        }
        String digest = asset.get("digest").getAsString();
        int colon = digest.indexOf(':');
        return colon < 0 ? null : digest.substring(colon + 1);
    }

    /** 网页端拿 tag：官方与镜像站并发请求。 */
    private String fetchTag() throws Exception {
        String path = "/" + OWNER + "/" + REPO + "/releases.atom";
        return readFirstMatching(path, ATOM_TAG, "Atom 订阅里找不到版本标签");
    }

    /** 产物名只有页面里才有，API 挂了就只能读这一页。 */
    private String fetchAssetName(String tag) throws Exception {
        String path = "/" + OWNER + "/" + REPO + "/releases/expanded_assets/" + tag;
        return readFirstMatching(path, EXPANDED_ASSET, "版本页面里找不到产物");
    }

    /**
     * 依次从 github.com 和镜像站取同一路径，返回第一个能匹配到的捕获组。
     *
     * <p>两边内容一致，区别只在网络路径。并发发起而不是排队等——github.com 在被
     * 屏蔽的网络下会卡满连接超时，「连得上却没数据」的情况下更要尽快放弃，
     * 否则开服会白白多等十几秒。
     */
    private String readFirstMatching(String path, Pattern pattern, String notFound) throws Exception {
        List<DownloadSource> candidates = List.of(
                new DownloadSource("官方", GITHUB + path),
                new DownloadSource("镜像站", MIRROR + path));

        SourceRacer.Hit hit;
        try {
            // 这一页只有几 KB，两条一起发，不用对冲延迟
            hit = racer.fetchFirst(candidates, Duration.ZERO);
        } catch (Exception e) {
            throw new IllegalStateException(notFound + "：" + e.getMessage(), e);
        }

        try (InputStream body = hit.response().body()) {
            Matcher matcher = pattern.matcher(new String(body.readAllBytes(), StandardCharsets.UTF_8));
            if (!matcher.find()) {
                throw new IllegalStateException(hit.source().label() + "的" + notFound);
            }
            return matcher.group(1);
        }
    }

    private static HttpRequest.Builder get(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(METADATA_TIMEOUT)
                .header("User-Agent", USER_AGENT);
    }

    private static String mirrorDownloadUrl(String tag, String asset) {
        return MIRROR + "/" + OWNER + "/" + REPO + "/releases/download/" + tag + "/" + asset;
    }

    /** API 路径之外的兜底：两个来源都按同样的规则拼地址。 */
    private static List<DownloadSource> sourcesFor(String tag, String asset) {
        List<DownloadSource> sources = new ArrayList<>();
        sources.add(new DownloadSource("官方",
                "https://github.com/" + OWNER + "/" + REPO + "/releases/download/" + tag + "/" + asset));
        sources.add(new DownloadSource("镜像站", mirrorDownloadUrl(tag, asset)));
        return sources;
    }
}
