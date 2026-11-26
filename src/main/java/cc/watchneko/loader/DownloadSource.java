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

/**
 * 一个可以下载产物的地址，带上用于日志的名字。
 *
 * <p>同一个版本通常有两个来源：GitHub 官方和镜像站。两者内容相同，
 * 区别只在网络路径，所以谁快就用谁。
 *
 * @param label 展示给用户的名字，例如 {@code 官方}、{@code 镜像站}
 * @param url   产物直链
 */
public record DownloadSource(String label, String url) {
}
