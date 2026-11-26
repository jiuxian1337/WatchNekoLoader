# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目定位

WatchNekoLoader 是一个 Bukkit/Spigot/Paper/Folia 插件，本身不提供任何游戏功能。它在服务端 `onEnable` 期间从**GitHub Releases** 拉取最新版 [WatchNeko](https://github.com/jiuxian1337/WatchNeko) 反作弊，加载并启用它，然后删掉下载的临时 jar。全部逻辑发生在开服过程中，**故意不注册任何命令/监听器/权限**。

## 常用命令

```bash
./gradlew build                  # 编译 + shadowJar + sourcesJar（产物在 build/libs/）
./gradlew shadowJar              # 只打最终 jar：build/libs/WatchNekoLoader-<yy.MM.dd>.jar
./gradlew runServer              # 起本地 Paper 1.8.8 开发服（已自动同意 EULA，-Xmx2G）
./gradlew clean                  # 清理构建产物
```

- 构建产物版本号由 `build.gradle.kts` 用当前日期 `yy.MM.dd` 生成，不是手工维护的版本常量。
- 没有测试目录（`src/test` 不存在）；`testImplementation(spigot-api)` 目前是占位，写测试前需先建目录。
- 编译需要 JDK 21（Gradle toolchain + `options.release = 21`），本机已验证 Zulu 21 可用。

## 架构

`WatchNekoLoaderPlugin` 只做编排，其余按职责分层：

| 类 | 职责 |
|---|---|
| `WatchNekoLoaderPlugin` | `onEnable` 编排：清理遗留 jar → 带重试下载 → 加载启用 → 删 jar；失败则关服 |
| `ReleaseClient` | 查最新版：优先 `api.github.com/releases/latest`，限流/失败时退到网页端解析 |
| `ReleaseInfo` | record：`version`、`assetName`、`sha256`（拿不到时 `null`）、`sources` |
| `DownloadSource` | record：一个下载地址 + 展示名（`官方` / `镜像站`） |
| `SourceRacer` | 多来源并发竞争，先返回 200 的胜出，落败的响应体自行关闭 |
| `ArtifactDownloader` | 流式下载 + 边读边算 SHA-256 + 进度条 + 卡死看门狗；校验失败删文件并抛异常 |

启动时序（`WatchNekoLoaderPlugin.onEnable`）：
1. `deleteLeftoverJars()` — 清掉 `getDataFolder()` 下所有 `.jar`，兜住上次「删失败」或「下到一半退出」
2. `downloadWithRetry()` — 最多 3 次，间隔 2s，**每次重试都重新查版本**；全部失败 → `getServer().shutdown()`
3. `loadAndEnable()` — `PluginManager.loadPlugin()` + **手动 `plugin.onLoad()`** + `enablePlugin()`；抛异常则关服，**保留 jar** 供排查
4. `deleteAfterLoad()` — POSIX 直接删；Windows 锁文件时退化为 `deleteOnExit()`

## 版本查询与下载的两条路（改之前务必读完）

网络环境是**中国大陆**，github.com 的实际可达性会出现波动（实测过同一台机器上 43/43 成功、随后 0/12 全部超时，而 `api.github.com` 和 release-assets CDN 仍然正常）。因此设计上做了两层冗余：

**查询**
- 首选 `https://api.github.com/repos/jiuxian1337/WatchNeko/releases/latest`。一次请求同时给出 tag、产物文件名和 `digest`（`sha256:` 前缀需剥掉）。
- 未认证的 API **按 IP 限流 60 次/小时**，共用出口 IP 的服务器很容易撞到 403。撞上就在日志里提示「改用网页端查询」，这是预期路径，不是故障。
- 网页端兜底：`releases.atom` 取 tag，`releases/expanded_assets/<tag>` 取产物名。这两个页面由 github.com 和镜像站**并发请求**（`Duration.ZERO` 对冲），谁先回用谁。这条路拿不到哈希，下载时跳过校验并打警告。

**下载**
- 两个来源：`github.com/.../releases/download/<tag>/<asset>` 与镜像站同路径。由 `SourceRacer` 并发竞争，第一个 200 胜出；镜像延迟 2.5s 发起，官方正常时不会白下一份。
- 实测官方通常更快（7–8 MB/s vs 4–6 MB/s），但被墙时会卡满连接超时，所以**不能**写成「官方失败再试镜像」的串行逻辑——那会白白多等 8–20 秒。

**两个必须保留的保护**
- `HttpRequest.timeout` 只管到响应头。收到头之后 body 卡住不动的场景（GFW 典型行为）由 `ArtifactDownloader.StallWatchdog` 兜：30 秒没有新数据就关流抛 `IOException`，交给上层重试。删掉它会让开服无限期挂住。
- 产物名**必须**取自 release 的 asset 名，不能由版本号拼。历史数据里有 tag `26.08.25` 对应文件 `WatchNeko-bukkit-26.08.28.jar`，更早的是 `edgrimac-bukkit-*`。

## 必须保持的不变量

这些是有意为之的设计决策，改动前先读相关注释与 README：

- **Fail-closed**：下载失败或加载失败一律 `getServer().shutdown()`，绝不降级为「无防护运行」。加载失败时 jar 必须保留，下载成功加载成功后才删。
- **`loadPlugin()` 不触发 `onLoad()`**：走这条绕过路径时必须手动调用 `plugin.onLoad()`，否则 WatchNeko 会跳过初始化。删掉这一行是隐蔽的破坏。
- **目标插件的异常不能吞掉也不能外泄**：`loadAndEnable` 内 catch 后返回 `false` 交给调用方关服，但必须打 `SEVERE` 日志。
- **不对用户暴露版本选择**：README 明确拒绝「指定版本」需求，否则又回到「永远用旧版」的老问题。
- **不留持久化文件**：临时 jar 每次都要清掉，`getDataFolder()` 不应越堆越多。

## 构建相关注意点

- **`plugin.yml` 是生成的**，源码里没有该文件。全部元数据（main、apiVersion 1.13、foliaSupported、softDepend 列表）都在 `build.gradle.kts` 的 `bukkit { }` 块里，改元数据去那里改。
- **Gson 被 shadow 并重定位** 到 `cc.watchneko.loader.libs`。原因是部分服务端分支的插件类加载器看不到服务端自带的 Gson，而加载器缺 JSON 库就起不来。新增 `implementation` 依赖时注意打进去会不会与插件环境冲突。Spigot API 是 `compileOnly`，不要改成 `implementation`。
- 仓库地址在 `ReleaseClient` 的 `OWNER` / `REPO`，镜像站地址是同一个类里的 `MIRROR`（`https://watchneko.zkmjnic.tech`）。注意镜像站只反代 github.com 的路径：`/api/latest`、`/versions/*` 这类旧接口会 301 到一个不通的 2280 端口，**不要去用**。
- `api.github.com` 不带 `User-Agent` 会直接 403，`ReleaseClient` 与 `SourceRacer` 都已带上。
- `runServer` 固定用 Minecraft 1.8.8 —— 这是 WatchNeko 的主要支持版本，虽然插件 apiVersion 声明为 1.13 且兼容 1.8~1.21。
- `apiVersion = "1.13"`，但代码只用 Java 21 标准库 + Bukkit 基础 API，且 `foliaSupported = true`：改代码时不要引入调度器/主线程假设，也不要调用 Folia 不支持的 API。`SourceRacer` 的线程都是 daemon，不会拖住关服。

## 约定

- 源码、注释、README、提交信息全部使用中文。日志文本是 README 里向用户展示的界面，改动会与文档不一致。
- 每个源文件顶部带 GPL-3.0 版权头与 `SPDX-License-Identifier: GPL-3.0-only`，新建文件需保持一致。
- 文件编码 UTF-8，`.gitattributes` 规定 `gradlew` 用 LF、`*.bat` 用 CRLF、`*.jar` 为 binary。
