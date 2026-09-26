package cc.watchneko.loader;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 从 WatchNekoWebSite 拉取最新 WatchNeko，在服务端启动时把它加载起来。
 *
 * <p>启动流程：清掉上次遗留的 jar → 查版本、下载、校验 → 加载并启用 → 删掉下载下来的 jar。
 * 下载失败最多重试 {@value #MAX_ATTEMPTS} 次，仍然失败就关服——没有反作弊就不开服。
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

        loadAndEnable(jar);
        deleteAfterLoad(jar);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
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

    /** 加载并启用。目标插件在 onLoad / onEnable 里抛的任何东西都不该把加载器一起带走。 */
    private void loadAndEnable(Path jar) {
        try {
            getLogger().info("正在加载最新最热WatchNeko");
            Plugin plugin = getServer().getPluginManager().loadPlugin(jar.toFile());
            plugin.onLoad();
            getServer().getPluginManager().enablePlugin(plugin);
        } catch (Exception e) {
            e.printStackTrace();
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
