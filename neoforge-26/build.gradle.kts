plugins {
    id("net.neoforged.moddev")
    id("com.gradleup.shadow")
}

neoForge {
    version = "26.3.0.115"
}

dependencies {
    implementation("thedarkcolour:kotlinforforge-neoforge:6.5.0")

    implementation(project(":core"))
    shadow(project(":core"))

    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveClassifier.set("slim")
}

tasks.shadowJar {
    archiveBaseName.set("peyajCustomDisc-NeoForge-26")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())

    configurations = listOf(project.configurations.getByName("shadow"))

    mergeServiceFiles()

    exclude("natives/win*/**")
    exclude("natives/darwin*/**")
    exclude("natives/mac*/**")
    exclude("org/slf4j/**")
    exclude("META-INF/services/org.slf4j.*")
    exclude("org/apache/commons/lang3/**")
    exclude("org/apache/commons/io/**")
    exclude("org/apache/commons/codec/**")
    exclude("org/jetbrains/annotations/**")
    exclude("org/intellij/lang/**")
    exclude("kotlin/**")
    exclude("kotlinx/**")
    exclude("_COROUTINE/**")
    exclude("META-INF/services/kotlin.*")
    exclude("META-INF/kotlin-*.kotlin_module")
    exclude("META-INF/proguard/**")
    exclude("META-INF/com.android.tools/**")
    exclude("META-INF/INDEX.LIST")
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("module-info.class")
    exclude("META-INF/versions/**/module-info.class")

    relocate("io.javalin", "com.peyaj.jukeboxweb.libs.javalin")
    relocate("org.eclipse.jetty", "com.peyaj.jukeboxweb.libs.jetty")
    relocate("jakarta.servlet", "com.peyaj.jukeboxweb.libs.jakarta.servlet")
    relocate("javax.servlet", "com.peyaj.jukeboxweb.libs.javax.servlet")
    relocate("com.fasterxml.jackson", "com.peyaj.jukeboxweb.libs.jackson")
    relocate("net.kyori", "com.peyaj.jukeboxweb.libs.kyori")
    relocate("net.bytebuddy", "com.peyaj.jukeboxweb.libs.bytebuddy")
    relocate("org.apache.commons.compress", "com.peyaj.jukeboxweb.libs.commons.compress")
    relocate("org.tukaani.xz", "com.peyaj.jukeboxweb.libs.xz")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
