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

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * 从多个来源并发拉取同一个对象，谁先成功用谁。
 *
 * <p>为什么不按顺序重试：实测 github.com 在某些网络下会「连得上但收不到任何数据」，
 * 直连要卡满连接超时才轮到下一个来源，开服整体会多等十几秒。并发发起以后，
 * 堵住的那条不影响另一条，正常网络下的总耗时也不会变差——官方快，它就是先回来的那个。
 *
 * <p>第一个来源立即发起，其余按 {@link #HEDGE_DELAY} 依次追加发起（对冲）：
 * 官方能在延迟内返回时，镜像根本不会被请求，不会白白多下一份。
 *
 * <p>失败的来源只记日志，全部失败才抛异常。
 */
final class SourceRacer {

    /** 单次连接的上限。堵住的连接必须尽快放弃，否则并发就失去意义。 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);

    /** 单个来源从发起到收到响应头的上限。 */
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(30);

    /** 发起下一个来源前给前一个留的时间。 */
    private static final Duration HEDGE_DELAY = Duration.ofMillis(2500);

    private static final String USER_AGENT = "jiuxian-baka-WatchNekoLoader/1.0";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            // 静态站/CDN 常用 302 把请求转到别处
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final Logger logger;

    SourceRacer(Logger logger) {
        this.logger = logger;
    }

    /** 一次成功的结果：它来自哪个来源、响应是什么。 */
    record Hit(DownloadSource source, HttpResponse<InputStream> response) {
    }

    /**
     * 并发请求所有来源，返回第一个 HTTP 200。
     *
     * @param hedgeDelay 发起下一个来源前给前一个留的时间。产物较大时用默认值避免白下一份；
     *                   响应很小（几百 KB 以下）时直接传 {@link Duration#ZERO}，两条一起发更划算
     * @throws Exception 所有来源都失败，携带最后一个失败原因
     */
    Hit fetchFirst(List<DownloadSource> sources, Duration hedgeDelay) throws Exception {
        CompletableFuture<Hit> winner = new CompletableFuture<>();
        List<CompletableFuture<HttpResponse<InputStream>>> pending = new ArrayList<>();
        AtomicInteger failures = new AtomicInteger();
        AtomicReference<Exception> lastError = new AtomicReference<>();
        // 竞速一旦结束（超时、异常、或已交出赢家），迟到的响应一律关掉。
        // 没有这个标志，调度器里正在跑的那次 launch 可能在 finally 遍历完 pending 之后才发请求，
        // 它的响应就再没人管了，连接会一直挂到进程退出。
        AtomicBoolean finished = new AtomicBoolean();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "WatchNekoLoader-hedge");
            thread.setDaemon(true);
            return thread;
        });

        Hit hit = null;
        try {
            for (int i = 0; i < sources.size(); i++) {
                DownloadSource source = sources.get(i);
                Runnable launch = () -> launch(source, sources.size(), pending,
                        failures, lastError, winner, finished);
                scheduler.schedule(launch, hedgeDelay.toMillis() * i, TimeUnit.MILLISECONDS);
            }

            long total = hedgeDelay.toMillis() * sources.size() + RESPONSE_TIMEOUT.toMillis() * 2;
            hit = awaitWinner(winner, total);
            return hit;
        } finally {
            finished.set(true);
            scheduler.shutdownNow();
            synchronized (pending) {
                for (var future : pending) {
                    future.cancel(true);
                }
            }
            // 抛出去的路径上赢家若已在最后关头就位，body 归这里关；正常返回时交给调用方
            if (hit == null) {
                Hit late = winner.getNow(null);
                if (late != null) {
                    close(late.response());
                }
            }
        }
    }

    /** {@code CompletableFuture.get} 会把异常包一层，这里拆开，日志里才看得懂。 */
    private static Hit awaitWinner(CompletableFuture<Hit> winner, long timeoutMillis) throws Exception {
        try {
            return winner.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }

    /** 产物下载用：给第一个来源留出时间，正常网络下不会白下一份。 */
    Hit fetchFirst(List<DownloadSource> sources) throws Exception {
        return fetchFirst(sources, HEDGE_DELAY);
    }

    private void launch(DownloadSource source, int sourceCount,
                        List<CompletableFuture<HttpResponse<InputStream>>> pending,
                        AtomicInteger failures, AtomicReference<Exception> lastError,
                        CompletableFuture<Hit> winner, AtomicBoolean finished) {
        // 竞速已经结束：这次对冲来晚了，不必再发请求
        if (finished.get() || winner.isDone()) {
            return;
        }

        CompletableFuture<HttpResponse<InputStream>> future;
        try {
            future = http.sendAsync(request(source), HttpResponse.BodyHandlers.ofInputStream());
        } catch (Exception e) {
            fail(source, sourceCount, failures, lastError, winner, e);
            return;
        }

        synchronized (pending) {
            pending.add(future);
        }

        future.whenComplete((response, error) -> {
            if (error != null) {
                fail(source, sourceCount, failures, lastError, winner, unwrap(error));
                return;
            }
            if (response.statusCode() != 200) {
                close(response);
                fail(source, sourceCount, failures, lastError, winner,
                        new IllegalStateException("HTTP " + response.statusCode()));
                return;
            }
            if (!winner.complete(new Hit(source, response))) {
                // 已经有更快的来源胜出，这条响应没人要了
                close(response);
            } else if (finished.get()) {
                // 与 finally 撞上了：它可能已经看过 winner 是空的并把 body 的归属判给了调用方
                close(response);
            }
        });
    }

    private void fail(DownloadSource source, int sourceCount, AtomicInteger failures,
                      AtomicReference<Exception> lastError, CompletableFuture<Hit> winner,
                      Exception error) {
        // 胜负已定后被取消的在途请求不该再报一次失败，它只是没人要了
        if (winner.isDone()) {
            return;
        }

        lastError.set(error);
        logger.warning(source.label() + "不可用：" + describe(error));

        if (failures.incrementAndGet() >= sourceCount) {
            winner.completeExceptionally(error);
        }
    }

    private static Exception unwrap(Throwable error) {
        return error instanceof Exception e ? e : new IllegalStateException(error);
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        return message != null ? message : error.getClass().getSimpleName();
    }

    private static void close(HttpResponse<InputStream> response) {
        try (var body = response.body()) {
            // 只为关闭连接
        } catch (Exception ignored) {
            // 关不掉也不影响结果
        }
    }

    private static HttpRequest request(DownloadSource source) {
        return HttpRequest.newBuilder()
                .uri(URI.create(source.url()))
                .timeout(RESPONSE_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .build();
    }
}
