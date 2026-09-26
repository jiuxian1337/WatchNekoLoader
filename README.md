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

需要 Java 21+。

## 构建

```bash
./gradlew build
```

产物在 `build/libs/`。
