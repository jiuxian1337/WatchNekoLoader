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

import java.util.List;

/**
 * GitHub 上的一条 release 记录。
 *
 * @param version 版本号，取自 release 的 tag，例如 {@code 26.10.31}
 * @param assetName 产物文件名，例如 {@code WatchNeko-bukkit-26.10.31.jar}。
 *                  <b>不能</b>用版本号拼出来：历史上有过 {@code 26.08.25} 的 release
 *                  挂着 {@code WatchNeko-bukkit-26.08.28.jar}，更早的还叫 {@code edgrimac-bukkit-*}
 * @param sha256  产物的 SHA-256（小写十六进制）。GitHub 的 {@code digest} 字段没有时
 *                （例如走 Atom 兜底路径）为 {@code null}，下载完会跳过校验
 * @param sources 下载地址，顺序即优先级
 */
public record ReleaseInfo(String version, String assetName, String sha256, List<DownloadSource> sources) {
}
