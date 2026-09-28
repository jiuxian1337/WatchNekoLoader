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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.logging.Logger;

/**
 * 把站点上的产物下到本地。
 *
 * <p>下载过程中每前进 10% 打一条进度。SHA-256 是边读边算的——不能为了校验把几百 MB 再读第二遍。
 * 校验不过就把文件删掉并抛异常，由上层决定要不要重试。
 */
public final class ArtifactDownloader {

    private static final int BAR_WIDTH = 20;
    private static final String USER_AGENT = "jiuxian-baka-WatchNekoLoader/1.0";

    private final WatchNekoSiteClient site;
    private final Path directory;
    private final Logger logger;

    private final HttpClient http = HttpClient.newBuilder()
            // 静态站/CDN 常用 302 把请求转到别处
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public ArtifactDownloader(WatchNekoSiteClient site, Path directory, Logger logger) {
        this.site = site;
        this.directory = directory;
        this.logger = logger;
    }

    /**
     * 查询最新版本并下载到 {@code directory}。
     *
     * @return 下好的 jar 的路径
     * @throws Exception 查询失败、下载失败，或 SHA-256 校验不通过
     */
    public Path download() throws Exception {
        VersionInfo version = site.fetchLatest();

        Files.createDirectories(directory);
        Path jar = directory.resolve(version.version() + ".jar");

        logger.info("准备开始下载最新最热WatchNeko");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(version.downloadUrl()))
                .header("User-Agent", USER_AGENT)
                .build();

        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("下载地址返回 HTTP " + response.statusCode());
        }

        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long received = copy(response.body(), jar, digest, total);
        logger.info("下载完成，共 " + received + " 字节");

        verify(digest.digest(), version, jar);
        return jar;
    }

    /** 一边写文件一边喂摘要器，顺便按进度打点。 */
    private long copy(InputStream in, Path jar, MessageDigest digest, long total) throws IOException {
        long received = 0;
        int lastStep = -1;

        try (in; OutputStream out = Files.newOutputStream(jar)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                received += read;

                if (total > 0) {
                    int step = (int) (received * 100 / total / 10);
                    if (step > lastStep) {
                        lastStep = step;
                        printProgress(received, total);
                    }
                }
            }
        }

        // 收个尾：某次读取正好跨过 100% 时循环里已经打过了，避免重复
        if (total > 0 && lastStep < 10) {
            printProgress(total, total);
        }
        return received;
    }

    /**
     * 比对站点登记的 SHA-256。
     *
     * <p>迁移过来的历史版本没有 fileHash，那种情况只打一条警告，不能让整次下载作废。
     */
    private void verify(byte[] hash, VersionInfo version, Path jar) throws IOException {
        if (version.fileHash() == null) {
            logger.warning("站点没有登记这个版本的 SHA-256，跳过哈希校验");
            return;
        }

        String actual = HexFormat.of().formatHex(hash);
        if (!actual.equalsIgnoreCase(version.fileHash())) {
            Files.deleteIfExists(jar);
            throw new IllegalStateException(
                    "SHA-256 校验失败（站点登记 " + version.fileHash() + "，实际 " + actual + "）");
        }
        logger.info("SHA-256 校验通过：" + actual);
    }

    /** 把进度打成一条日志，例如 {@code 下载中 [########------------] 40% (3.8/9.4MB)}。 */
    private void printProgress(long received, long total) {
        int percent = (int) (received * 100 / total);
        int filled = percent * BAR_WIDTH / 100;

        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < BAR_WIDTH; i++) {
            bar.append(i < filled ? '#' : '-');
        }
        bar.append(']');

        logger.info("下载中 " + bar + " " + percent + "% ("
                + toMb(received) + "/" + toMb(total) + "MB)");
    }

    private static String toMb(long bytes) {
        return String.format("%.1f", bytes / 1024.0 / 1024.0);
    }
}
