<div align="center">

# WatchNekoLoader

**每次开服，自动用上最新的反作弊。**

你什么都不用做。把文件放进服务器，以后每次开服它都会自己去下载最新的 [WatchNeko](https://github.com/jiuxian1337/WatchNeko) 反作弊，装好，然后删掉下载的临时文件。

[![Java](https://img.shields.io/badge/Java-21%2B-orange?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.8%20~%201.21-green)](https://www.spigotmc.org/)
[![Folia](https://img.shields.io/badge/Folia-supported-blueviolet)](#常见问题)
[![Release](https://img.shields.io/github/v/release/jiuxian1337/WatchNekoLoader?color=brightgreen&label=release)](https://github.com/jiuxian1337/WatchNekoLoader/releases)
[![License](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)

</div>

---

## 它是干什么的

反作弊这个插件很特殊：**它必须一直是最新的才有用。**

别的插件落后几个版本，无非是少几个功能。反作弊落后几个版本，意味着已经有人找到了绕过它的办法——而你可能完全不知道。所以反作弊的作者更新得很勤，几乎每周都有新版本。

问题是，手动更新一次很麻烦：打开官网、找到最新版、下载、关服、删掉旧的、放进新的、再开服。做一次还行，每周做一次，大多数人就不做了。**于是很多服务器的反作弊，从装上那天起就再没更新过。**

这个插件就是为了解决这件事。装上它以后，更新反作弊这件事你彻底不用管了。

## 安装

只做三步：

**第一步**：从 [Releases](https://github.com/jiuxian1337/WatchNekoLoader/releases) 页面下载文件名以 `WatchNekoLoader-` 开头的那个 jar 文件

**第二步**：把这个 jar 文件放进服务器的 `plugins` 文件夹里（和你的其他插件放在一起）

**第三步**：重启服务器

就这些。没有配置文件要改，也不需要你去官网下载任何东西。

> 📺 **更喜欢看视频？** B 站有完整教程，[点这里](https://www.bilibili.com/video/BV1pZa46cE81) 直接看。

> ⚠️ **注意**：你的服务器需要 **Java 21 或更高版本**。如果开服时报错说 Java 版本太低，去 [这里](https://adoptium.net/) 下载新版 Java 装上即可。

## 装好之后会发生什么

每次开服，你都会在控制台里看到这样的画面：

```log
[WatchNekoLoader] 已获取到最新最热WatchNeko版本：26.09.27
[WatchNekoLoader] 准备开始下载最新最热WatchNeko
[WatchNekoLoader] 下载中 [########------------] 40% (3.8/9.4MB)
[WatchNekoLoader] 下载完成
[WatchNekoLoader] 正在加载最新最热WatchNeko
```

看到进度条走完、提示正在加载，就说明成功了。进游戏以后，你的服务器就已经在用它自己刚下好的最新版反作弊了。

整个过程通常只要几秒钟，你不需要做任何操作。

## 如果下载失败了

网络出问题的时候会重试，一共试 3 次，每次相隔 2 秒。

三次都没成功的话，**服务器会直接关闭，不会启动**。

下载成功但反作弊没能装起来（比如文件本身有问题），结果也一样——**关服，不会硬着头皮开着**。这种情况下下载的文件会保留下来，方便你查原因。

这些都是故意这么设计的——没有反作弊就不开服，总比开一个没有防护、可能被人随便破坏的服务器要好。遇到这种情况，检查一下服务器能不能正常上网，然后重新开服就行。

## 常见问题

**我需要先装 WatchNeko 吗？**
不需要。你只要装这一个插件就够了，WatchNeko 本体由它自动下载。

**以后更新 WatchNeko 要做什么？**
什么都不用做。每次开服都是最新的。

**下载下来的文件放在哪？会不会越堆越多？**
装好之后就自动删掉了，不会占地方，也不会越堆越多。

**能不能让它用指定的某个版本？**
不能。如果允许指定版本，就又会回到「一直用旧版本」的老问题上，那这个插件就白做了。

**支持哪些服务器？**
Spigot、Paper 以及它们的分支都支持，Folia 也支持。Minecraft 1.8 到 1.21 都可以。如果你装的其他插件比较多，也不用担心冲突。

**官网打不开怎么办？**
重试 3 次后服务器会关闭。等官网恢复正常再开服就好。

## 还是没弄好？

**加 QQ 群，直接问人：**

👉 [点击链接加入群聊【WatchNeko】](https://qm.qq.com/q/3f4Touszsc)

装不上、报错看不懂、不确定该怎么做，都可以进群问。想先自己看看也行，这里有视频教程：

📺 **《[免费开源] WatchNeko 反作弊使用教程》** → [B 站观看](https://www.bilibili.com/video/BV1pZa46cE81)

## 相关项目

| 项目 | 说明 |
|---|---|
| [WatchNeko](https://github.com/jiuxian1337/WatchNeko) | 反作弊本体，本插件自动下载的就是它 |

## 许可

本项目使用 **GPL-3.0** 许可证，全文见 [LICENSE](LICENSE)。

简单说：你可以免费使用、修改、分享它，怎么用都行；但如果你改了它再发布出去，你必须同样公开你的修改。WatchNeko 本体和它上游的 Grim 也都是 GPL-3.0，这里保持一致。
