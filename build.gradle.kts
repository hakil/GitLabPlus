plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.20.1"
}

group = "com.caidailian"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // A：使用本机 IDEA 安装目录 —— 零下载，编译与运行时同一 build
        local(providers.gradleProperty("platformLocalPath").get())
        // B（备选，切换时注释掉 A）：自动下载对应发行包
        // intellijIdea("2026.2")

        // 编译期需要 git4idea 的类；运行期由宿主插件提供，
        // 因此同时需要在 plugin.xml 声明 <depends>Git4Idea</depends>
        bundledPlugin("Git4Idea")
    }

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(
            providers.gradleProperty("javaVersion").get().toInt()
        )
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.caidailian.gitlabplus"
        name = "GitLab+ Merge Tool"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = providers.gradleProperty("sinceBuild").get()
        }
    }

    // 不使用搜索索引与 @NotNull 断言插桩
    buildSearchableOptions = false
    instrumentCode = false
}

tasks {
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    // 结构与工程配置校验挂在 check 上；verifyPlugin（二进制兼容性）耗时较长，按需手动执行：
    //   gradlew verifyPlugin
    named("check") {
        dependsOn(
            "verifyPluginProjectConfiguration",
            "verifyPluginStructure"
        )
    }
}
