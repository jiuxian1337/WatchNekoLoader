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
