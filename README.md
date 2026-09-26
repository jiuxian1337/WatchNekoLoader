# WatchNekoLoader

在服务端启动时从 [WatchNeko 官网](https://watchneko.zkmjnic.tech/) 拉取最新发布的
[WatchNeko](https://github.com/jiuxian1337/WatchNeko) 反作弊，校验后在运行时加载起来。

## 做什么

启动时依次做四件事：

1. 查站点上最新发布的版本，下载产物并显示进度条；
2. 校验下载内容的 SHA-256；
3. 加载并启用 WatchNeko；
4. 删掉下载下来的 jar。

下载失败会重试，最多 3 次；三次都失败就关闭服务器——没有反作弊就不开服。

## 安装

1. 把 `WatchNekoLoader-<版本>.jar` 放进 `plugins/`；
2. 启动服务端。

需要 Java 21+。另外 WatchNeko 自己依赖的插件（例如 packetevents，取决于你用的是哪份构建）也要装好。

## 构建

```bash
./gradlew build
```

产物在 `build/libs/`。

## 说明

- 站点地址写在 `WatchNekoLoaderPlugin.SITE`，换域名时改这一处。
- 已经在运行的 WatchNeko 卸不掉（Bukkit 没有卸载插件的 API），所以更新版本要重启服务端。
- 从旧的手工安装迁过来时，WatchNeko 的配置与数据库位置可能会变，启动日志里会有提示。
