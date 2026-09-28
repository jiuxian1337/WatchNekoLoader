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

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

/**
 * 从 WatchNekoWebSite 拉取最新 WatchNeko，在服务端启动时把它加载起来。
 *
 * <p>启动流程：清掉上次遗留的 jar → 查版本、下载、校验 → 加载并启用 → 删掉下载下来的 jar。
 * 下载失败最多重试 {@value #MAX_ATTEMPTS} 次，仍然失败就关服——没有反作弊就不开服。
 * 下载成功但加载失败同样关服，且这种情况下保留 jar 供排查。
 *
 * <p>本类只管编排，具体怎么查版本见 {@link WatchNekoSiteClient}，怎么下载校验见
 * {@link ArtifactDownloader}。
 */
public final class WatchNekoLoaderPlugin extends JavaPlugin {

    /** 站点地址，换域名改这一处。 */
    private static final String SITE = "https://watchneko.zkmjnic.tech";

    /** 下载失败时的总尝试次数，用完还失败就关服。 */
    private static final int MAX_ATTEMPTS = 3;

    /** 两次尝试之间的间隔。不给停顿的话三次会在同一瞬间全部失败，重试就没有意义了。 */
    private static final long RETRY_DELAY_MILLIS = 2000L;

    @Override
    public void onEnable() {
        deleteLeftoverJars();

        ArtifactDownloader downloader = new ArtifactDownloader(
                new WatchNekoSiteClient(SITE, getLogger()), getDataFolder().toPath(), getLogger());

        Path jar = downloadWithRetry(downloader);
        if (jar == null) {
            getLogger().severe("连续 " + MAX_ATTEMPTS + " 次都没能下到 WatchNeko，没有反作弊就不开服了");
            getServer().shutdown();
            return;
        }

        if (!loadAndEnable(jar)) {
            getLogger().severe("WatchNeko 没能加载起来，没有反作弊就不开服了");
            getLogger().severe("下载的 jar 保留在 " + jar + "，可据此排查问题");
            getServer().shutdown();
            return;
        }

        deleteAfterLoad(jar);
    }

    /** 反复尝试下载，全部失败返回 {@code null}。 */
    private Path downloadWithRetry(ArtifactDownloader downloader) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return downloader.download();
            } catch (Exception e) {
                getLogger().warning("第 " + attempt + "/" + MAX_ATTEMPTS + " 次下载失败：" + e);
                if (attempt == MAX_ATTEMPTS || !delay()) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * 加载并启用。
     *
     * <p>{@code loadPlugin} 只登记插件，不会触发 {@code onLoad}——那是服务端加载普通插件时自己做的，
     * 这里绕过了那条路径，所以要手动补上，否则 WatchNeko 会跳过它的初始化。
     *
     * <p>目标插件在 onLoad / onEnable 里抛的任何东西都不该把加载器一起带走，但也绝不能吞掉：
     * 加载失败意味着服务端即将在无防护状态下运行，这个结果由调用方处理。
     *
     * @return 是否成功加载并启用
     */
    private boolean loadAndEnable(Path jar) {
        try {
            getLogger().info("正在加载最新最热WatchNeko");
            Plugin plugin = getServer().getPluginManager().loadPlugin(jar.toFile());
            plugin.onLoad();
            getServer().getPluginManager().enablePlugin(plugin);
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "WatchNeko 加载失败", e);
            return false;
        }
    }

    /** 清掉上次遗留的产物。正常情况下每次加载完都会删，这里兜住「删失败」和「下到一半就退出」。 */
    private void deleteLeftoverJars() {
        File[] leftovers = getDataFolder().listFiles();
        if (leftovers == null) {
            return;
        }

        for (File file : leftovers) {
            if (file.getName().endsWith(".jar")) {
                file.delete();
            }
        }
    }

    /**
     * 删掉刚从其中加载起来的 jar。
     *
     * <p>类这时已经读进内存，POSIX 上删一个还开着的文件没问题；Windows 上锁着删不掉，
     * 就交给 {@code deleteOnExit()}，下次启动的清理还会再补一遍。
     */
    private void deleteAfterLoad(Path jar) {
        try {
            Files.deleteIfExists(jar);
        } catch (Exception e) {
            if (Files.exists(jar)) {
                jar.toFile().deleteOnExit();
            }
        }
    }

    /** @return 是否顺利等完；被中断时返回 false */
    private boolean delay() {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
