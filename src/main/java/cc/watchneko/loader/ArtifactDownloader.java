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
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * 把 release 里的产物下到本地。
 *
 * <p>多个来源由 {@link SourceRacer} 并发竞争，先响应且校验通过的那个胜出；
 * 胜出的来源如果半路断了，剩下的来源里没有第二个已建立的连接可接手，
 * 由上层重试兜住（重试会重新竞争一次）。
 *
 * <p>下载过程中每前进 10% 打一条进度。SHA-256 是边读边算的——不能为了校验把文件再读第二遍。
 * 校验不过就把文件删掉并抛异常，由上层决定要不要重试。
 */
public final class ArtifactDownloader {

    private static final int BAR_WIDTH = 20;

    private final Path directory;
    private final Logger logger;
    private final SourceRacer racer;

    public ArtifactDownloader(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
        this.racer = new SourceRacer(logger);
    }

    /**
     * 把 {@code release} 的产物下载到 {@code directory}。
     *
     * @return 下好的 jar 的路径
     * @throws Exception 所有来源都失败，或 SHA-256 校验不通过
     */
    public Path download(ReleaseInfo release) throws Exception {
        Files.createDirectories(directory);
        // 文件名直接取 release 里的产物名：它和真实 asset 一致，用版本号拼会拼错
        Path jar = directory.resolve(release.assetName());

        logger.info("准备开始下载最新最热WatchNeko");
        SourceRacer.Hit hit = racer.fetchFirst(release.sources());

        // 胜出的响应体归这里管；无论后面成功与否都要关掉，否则连接会一直挂着
        try (InputStream body = hit.response().body()) {
            long total = hit.response().headers().firstValueAsLong("Content-Length").orElse(-1L);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long received = copy(body, jar, digest, total);

            // 没有哈希可比时（Atom 兜底路径），长度就是唯一的完整性依据：
            // 连接在最后一个完整缓冲区处断开时 read 会正常返回 -1，不比对就会把残缺的 jar 当成功
            if (total > 0 && received != total) {
                throw new IllegalStateException(
                        "下载不完整（应为 " + total + " 字节，实际 " + received + " 字节）");
            }

            logger.info("下载完成，共 " + received + " 字节（来源：" + hit.source().label() + "）");
            verify(digest.digest(), release, jar);
            return jar;
        }
    }

    /**
     * 一边写文件一边喂摘要器，顺便按进度打点。
     *
     * <p>读 body 时 HttpRequest 的超时已经失效了（它只管到响应头），所以单靠它挡不住
     * 「连上了、头也回来了、然后一个字节都不再来」——那会让开服无限期挂着。
     * 这里用一个看门狗盯着最后一次收到数据的时间，超过 {@link #STALL_TIMEOUT} 就关流，
     * 让 {@code read} 抛异常，交给上层重试。
     */
    private long copy(InputStream in, Path jar, MessageDigest digest, long total) throws IOException {
        long received = 0;
        int lastStep = -1;
        StallWatchdog watchdog = StallWatchdog.start(in);

        try (in; OutputStream out = Files.newOutputStream(jar)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                watchdog.progress();
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
        } finally {
            watchdog.stop();
        }

        // 收个尾：某次读取正好跨过 100% 时循环里已经打过了，避免重复
        if (total > 0 && lastStep < 10) {
            printProgress(total, total);
        }
        return received;
    }

    /** 在后台盯着传输是否卡死，卡住就关掉流。 */
    private static final class StallWatchdog {

        private static final Duration STALL_TIMEOUT = Duration.ofSeconds(30);
        private static final Duration CHECK_INTERVAL = Duration.ofSeconds(5);

        private final InputStream stream;
        private final AtomicLong lastActivity = new AtomicLong(System.nanoTime());
        private final AtomicBoolean stopped = new AtomicBoolean();
        private final ScheduledExecutorService scheduler;

        private StallWatchdog(InputStream stream) {
            this.stream = stream;
            this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "WatchNekoLoader-stall-watchdog");
                thread.setDaemon(true);
                return thread;
            });
        }

        static StallWatchdog start(InputStream stream) {
            StallWatchdog watchdog = new StallWatchdog(stream);
            long timeoutNanos = STALL_TIMEOUT.toNanos();
            watchdog.scheduler.scheduleWithFixedDelay(() -> {
                if (System.nanoTime() - watchdog.lastActivity.get() <= timeoutNanos) {
                    return;
                }
                // 关流会让阻塞中的 read 抛 IOException，异常里有这条信息可供排查
                watchdog.stop();
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // 已经断了
                }
            }, CHECK_INTERVAL.toMillis(), CHECK_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
            return watchdog;
        }

        void progress() {
            lastActivity.set(System.nanoTime());
        }

        void stop() {
            if (stopped.compareAndSet(false, true)) {
                scheduler.shutdownNow();
            }
        }
    }

    /**
     * 比对 release 登记的 SHA-256。
     *
     * <p>走 Atom 兜底路径时拿不到哈希，那种情况只打一条警告，不能让整次下载作废。
     */
    private void verify(byte[] hash, ReleaseInfo release, Path jar) throws IOException {
        if (release.sha256() == null) {
            logger.warning("没拿到这个版本的 SHA-256，跳过哈希校验");
            return;
        }

        String actual = HexFormat.of().formatHex(hash);
        if (!actual.equalsIgnoreCase(release.sha256())) {
            Files.deleteIfExists(jar);
            throw new IllegalStateException(
                    "SHA-256 校验失败（release 登记 " + release.sha256() + "，实际 " + actual + "）");
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
