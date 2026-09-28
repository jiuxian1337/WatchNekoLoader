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
 * 站点上的一条版本记录。
 *
 * @param version     版本号，例如 {@code 26.09.27}
 * @param downloadUrl 产物下载地址。站点不提供下载接口，这个地址是客户端拼出来的
 * @param fileHash    产物的 SHA-256（小写十六进制）。迁移过来的历史版本没有，此时为 {@code null}，
 *                    下载完会跳过校验
 */
public record VersionInfo(String version, String downloadUrl, String fileHash) {
}
