import org.gradle.api.attributes.java.TargetJvmVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm") version "2.4.20" apply false
    id("com.gradleup.shadow") version "9.6.1" apply false
    id("net.fabricmc.fabric-loom") version "1.17.20" apply false
    id("net.fabricmc.fabric-loom-remap") version "1.17.20" apply false
}

allprojects {
    group = "com.peyaj"
    version = "2.5"

    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.maxhenkel.de/repository/public")
        maven("https://maven.lavalink.dev/releases")
        maven("https://maven.topi.wtf/releases")
        maven("https://jitpack.io")
        maven("https://maven.lavalink.dev/snapshots")
        maven("https://maven.enginehub.org/repo/") // WorldGuard/WorldEdit
        maven("https://repo.codemc.org/repository/maven-public/")
        maven("https://repo.opencollab.dev/main/")
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    configure<KotlinJvmProjectExtension> {
        jvmToolchain(25)
    }

    configurations.matching { it.name == "compileClasspath" || it.name == "runtimeClasspath" }.configureEach {
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
        }
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(21)
    }
}

tasks.register("shadowJar") {
    group = "build"
    description = "Assembles shadow jars for runnable platform subprojects."
    dependsOn(":paper:shadowJar", ":fabric-1.21:remapJar", ":fabric-26:shadowJar")
}

