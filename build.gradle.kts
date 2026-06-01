plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.14.0"
}

group = "dev.vishv.phpstorm"
version = "0.1.2"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        phpstorm("2026.1.2")
        bundledPlugin("org.jetbrains.plugins.terminal")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
            untilBuild = "261.*"
        }
    }
}

tasks.matching { it.name in setOf("buildSearchableOptions", "prepareJarSearchableOptions", "jarSearchableOptions") }.configureEach {
    enabled = false
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
