import java.text.SimpleDateFormat
import java.util.Date

plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.6.1"
    id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "cc.watchneko"
version = SimpleDateFormat("yy.MM.dd").format(Date())
description = "在服务端启动时从 WatchNekoWebSite 拉取并加载最新 WatchNeko 反作弊"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") // Spigot API
    maven("https://repo.papermc.io/repository/maven-public/") // run-paper 拉取的 Paper 服务端
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.18.2-R0.1-SNAPSHOT")

    // Gson 会被打进 jar 并重定位。服务端自带的那份不保证在所有分支上都对插件可见，
    // 而这个加载器不能因为缺一个 JSON 库就起不来。
    implementation("com.google.code.gson:gson:2.13.1")

    testImplementation("org.spigotmc:spigot-api:1.18.2-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // WatchNeko 本体要求 Java 17+，加载器与它同进程运行，对齐即可
    options.release.set(21)
}

tasks.shadowJar {
    archiveFileName.set("${rootProject.name}-${project.version}.jar")
    // 服务端已经有一份 Gson 时，插件类加载器仍可能看不到它，重定位后互不干扰
    relocate("com.google.gson", "cc.watchneko.loader.libs.gson")
}

tasks.named("assemble") {
    dependsOn(tasks.shadowJar)
}

bukkit {
    name = "WatchNekoLoader"
    version = project.version.toString()
    main = "cc.watchneko.loader.WatchNekoLoaderPlugin"
    description = project.description
    authors = listOf("P01_4rU5er")
    website = "https://watchneko.zkmjnic.tech"
    apiVersion = "1.13"
    foliaSupported = true

    softDepend = listOf(
        "ProtocolLib",
        "ProtocolSupport",
        "Essentials",
        "ViaVersion",
        "ViaBackwards",
        "ViaRewind",
        "Geyser-Spigot",
        "floodgate",
        "FastLogin",
        "PlaceholderAPI",
    )

    commands {
        register("watchnekoloader") {
            description = "查看/更新由本加载器托管的 WatchNeko"
            aliases = listOf("wnl", "wnloader")
            usage = "/watchnekoloader <status|check|update|reload>"
            permission = "watchneko.loader.command"
        }
    }

    permissions {
        register("watchneko.loader.command") {
            description = "使用 /watchnekoloader 的基础子命令（status、check）"
            default = net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.OP
        }

        register("watchneko.loader.update") {
            description = "执行会改动服务端文件的子命令（update、reload）"
            default = net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.OP
        }
    }
}

tasks.runServer {
    // 这里不能用 1.8.8：run-paper 用的是本项目的 Java 工具链（21）去起服务端，
    // 而 1.8.8 的 patcher 和服务端都跑不了 Java 21（实测报 "Failed to patch vanilla jar"）。
    // WatchNeko 本身要求 Java 17+，所以开发服只能用现代版本。
    minecraftVersion("1.8.8")
    systemProperty("com.mojang.eula.agree", "true")
    jvmArgs("-Xmx2G", "-Xms2G")
}
