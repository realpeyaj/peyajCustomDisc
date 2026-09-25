pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        mavenCentral()
    }
}

rootProject.name = "peyajCustomDisc"

include("core")
include("paper")
include("fabric-1.21")
include("fabric-26")

