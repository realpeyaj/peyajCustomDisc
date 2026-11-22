pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases")
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "peyajCustomDisc"

include("core")
include("paper")
include("fabric-1.21")
include("fabric-26")
include("neoforge-1.21")
include("neoforge-26")

