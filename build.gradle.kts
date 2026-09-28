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
    relocate("com.google", "cc.watchneko.loader.libs")
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

    // 本插件不注册任何命令：它的全部工作发生在服务端启动过程中，
    // 更新也由下次开服自动完成，没有需要玩家或管理员手动触发的动作。
}

tasks.runServer {
    // 1.8.8 是 WatchNeko 最主要支持的版本，开发服跟着主要使用场景走。
    // 注意：run-paper 用本项目的 Java 工具链（21）起服务端，1.8.8 的 patcher 在 Java 21 下
    // 可能报 "Failed to patch vanilla jar"；真遇到就换低版本 JDK 单独跑这个任务。
    minecraftVersion("1.8.8")
    systemProperty("com.mojang.eula.agree", "true")
    jvmArgs("-Xmx2G", "-Xms2G")
}
